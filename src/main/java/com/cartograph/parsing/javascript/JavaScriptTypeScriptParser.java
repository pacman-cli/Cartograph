package com.cartograph.parsing.javascript;

import com.cartograph.application.SourceParser;
import com.cartograph.graph.StableNodeId;
import com.cartograph.graph.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.treesitter.*;

/** Tree-sitter adapter. All relationship decisions use AST nodes and lexical scopes. */
public final class JavaScriptTypeScriptParser implements SourceParser {
    private static final Set<String> EXTENSIONS = Set.of("js", "jsx", "ts", "tsx");
    private static final Set<String> DEFINITION_TYPES =
            Set.of("function_declaration", "class_declaration", "method_definition");
    private static final Set<String> SCOPE_TYPES = Set.of(
            "program",
            "statement_block",
            "function_declaration",
            "method_definition",
            "arrow_function",
            "function",
            "class_declaration",
            "for_statement",
            "for_in_statement",
            "for_of_statement",
            "catch_clause");

    @Override
    public ParsedFile parse(SourceFile file) {
        String path = StableNodeId.normalizePath(file.path());
        String extension = extension(path);
        if (!EXTENSIONS.contains(extension))
            return empty(path, new GraphWarning("UNSUPPORTED_FILE", "Unsupported source extension", path, null));
        TSLanguage language =
                switch (extension) {
                    case "ts" -> new TreeSitterTypescript();
                    case "tsx" -> new TreeSitterTsx();
                    default -> new TreeSitterJavascript();
                };
        try (TSParser parser = new TSParser();
                TSLanguage ignored = language) {
            if (!parser.setLanguage(language)) throw new IllegalStateException("Incompatible tree-sitter grammar");
            try (TSTree tree = parser.parseString(null, file.content())) {
                TSNode root = tree.getRootNode();
                List<GraphWarning> warnings = new ArrayList<>();
                if (root.hasError())
                    warnings.add(new GraphWarning(
                            "SYNTAX_ERROR", "Tree-sitter recovered from invalid syntax", path, line(root)));
                List<GraphNode> nodes = new ArrayList<>();
                List<GraphEdge> edges = new ArrayList<>();
                List<String> exports = new ArrayList<>();
                List<GraphExport> exportDetails = new ArrayList<>();
                List<DirectCallSite> callSites = new ArrayList<>();
                GraphNode fileNode = node(path, SymbolKind.FILE, path, root, file.content());
                nodes.add(fileNode);
                List<Definition> definitions = new ArrayList<>();
                walk(root, n -> {
                    if (DEFINITION_TYPES.contains(n.getType())) {
                        Definition definition = definition(n, file.content(), path);
                        if (definition != null) {
                            definitions.add(definition);
                            nodes.add(definition.node);
                            edges.add(new GraphEdge(
                                    fileNode.stableId(),
                                    definition.node.stableId(),
                                    EdgeKind.CONTAINS,
                                    1.0,
                                    definition.node.location()));
                        }
                    } else if (n.getType().equals("import_statement")) {
                        String module = importedModule(n, file.content());
                        if (module != null) {
                            GraphNode moduleNode = node(path, SymbolKind.MODULE, module, n, file.content());
                            nodes.add(moduleNode);
                            edges.add(new GraphEdge(
                                    moduleNode.stableId(),
                                    fileNode.stableId(),
                                    EdgeKind.IMPORTS,
                                    1.0,
                                    moduleNode.location()));
                        }
                    } else if (n.getType().equals("export_statement")) {
                        exportDetails.addAll(exportNames(n, file.content(), path));
                    } else if (n.getType().equals("call_expression")) {
                        TSNode target = n.getChildByFieldName("function");
                        if (target.isNull() || !target.getType().equals("identifier")) {
                            warnings.add(new GraphWarning(
                                    "UNRESOLVED_CALL",
                                    "Call target cannot be proven: " + text(target, file.content()),
                                    path,
                                    line(n)));
                            return;
                        }
                        String name = text(target, file.content());
                        SourceLocation location = location(path, n);
                        callSites.add(new DirectCallSite(name, location));
                        Definition caller = enclosingDefinition(n, definitions);
                        Definition resolved = resolve(name, caller, n, definitions, file.content());
                        if (resolved == null)
                            warnings.add(new GraphWarning(
                                    "UNRESOLVED_CALL", "Call target cannot be proven: " + name, path, line(n)));
                        else
                            edges.add(new GraphEdge(
                                    caller == null ? fileNode.stableId() : caller.node.stableId(),
                                    resolved.node.stableId(),
                                    EdgeKind.CALLS,
                                    1.0,
                                    location));
                    } else if (n.getType().equals("arrow_function")) {
                        warnings.add(new GraphWarning(
                                "UNSUPPORTED_SYNTAX", "Arrow function definitions are not graph nodes", path, line(n)));
                    }
                });
                exports.addAll(exportDetails.stream().map(GraphExport::name).toList());
                nodes.sort(Comparator.comparing(GraphNode::startLine)
                        .thenComparing(n -> n.kind() == SymbolKind.FILE ? 0 : 1)
                        .thenComparing(GraphNode::name));
                List<GraphEdge> distinctEdges = edges.stream().distinct().toList();
                List<GraphWarning> sortedWarnings = warnings.stream()
                        .distinct()
                        .sorted(Comparator.comparing(
                                w -> Optional.ofNullable(w.line()).orElse(0)))
                        .toList();
                return new ParsedFile(path, nodes, distinctEdges, sortedWarnings, exports, exportDetails, callSites);
            }
        }
    }

    private static Definition resolve(
            String name, Definition caller, TSNode call, List<Definition> definitions, String source) {
        ScopeBinding binding = nearestBinding(name, call, definitions, source);
        if (binding == null || binding.mutated) return null;
        if (binding.definition == null || !visibleInScope(binding.definition.ast, call)) return null;
        return binding.definition;
    }

    private static boolean visibleInScope(TSNode declaration, TSNode use) {
        TSNode parent = declaration.getParent();
        while (!parent.isNull()) {
            if (parent.getType().equals("function_declaration")
                    || parent.getType().equals("method_definition")
                    || parent.getType().equals("arrow_function")) return contains(parent, use);
            parent = parent.getParent();
        }
        return true;
    }

    private static ScopeBinding nearestBinding(String name, TSNode node, List<Definition> definitions, String source) {
        TSNode scope = node;
        while (!scope.isNull()) {
            if (isScope(scope)) {
                ScopeBinding binding = bindingInScope(name, scope, definitions, source);
                if (binding != null) return binding;
            }
            scope = scope.getParent();
        }
        return null;
    }

    private static ScopeBinding bindingInScope(String name, TSNode scope, List<Definition> definitions, String source) {
        List<TSNode> declarations = new ArrayList<>();
        List<TSNode> mutations = new ArrayList<>();
        collectScope(scope, scope, name, declarations, mutations, source);
        if (scope.getType().equals("function_declaration")
                || scope.getType().equals("method_definition")
                || scope.getType().equals("arrow_function")) {
            TSNode parameters = scope.getChildByFieldName("parameters");
            if (!parameters.isNull()) {
                collectPattern(parameters, name, declarations, source);
                walk(parameters, child -> {
                    if (child.getType().equals("identifier")
                            && text(child, source).equals(name)
                            && !declarations.contains(child)) declarations.add(child);
                    if (child.getType().contains("pattern")
                            && text(child, source).trim().equals(name)
                            && !declarations.contains(child)) declarations.add(child);
                });
            }
        }
        if (declarations.isEmpty() && mutations.isEmpty()) return null;
        Definition definition = declarations.stream()
                .map(declaration -> definitions.stream()
                        .filter(candidate -> candidate.ast.getStartByte() == declaration.getStartByte())
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new ScopeBinding(definition, !mutations.isEmpty());
    }

    private static void collectScope(
            TSNode root,
            TSNode current,
            String name,
            List<TSNode> declarations,
            List<TSNode> mutations,
            String source) {
        String type = current.getType();
        if (!current.equals(root) && isScope(current)) {
            if (type.equals("function_declaration") || type.equals("class_declaration")) {
                TSNode declaredName = current.getChildByFieldName("name");
                if (!declaredName.isNull() && text(declaredName, source).equals(name)) declarations.add(current);
            }
            return;
        }
        if (type.equals("variable_declarator")) {
            TSNode declaredName = current.getChildByFieldName("name");
            if (!declaredName.isNull()) collectPattern(declaredName, name, declarations, source);
        }
        if (type.equals("import_specifier") || type.equals("namespace_import") || type.equals("import_clause")) {
            TSNode declaredName = current.getChildByFieldName("alias");
            if (declaredName.isNull()) declaredName = current.getChildByFieldName("name");
            if (!declaredName.isNull() && text(declaredName, source).equals(name)) declarations.add(declaredName);
        }
        if (type.equals("assignment_expression") || type.equals("augmented_assignment_expression")) {
            TSNode left = current.getChildByFieldName("left");
            if (!left.isNull()
                    && left.getType().equals("identifier")
                    && text(left, source).equals(name)) mutations.add(left);
        }
        if (type.equals("update_expression")) {
            TSNode argument = current.getChildByFieldName("argument");
            if (!argument.isNull()
                    && argument.getType().equals("identifier")
                    && text(argument, source).equals(name)) mutations.add(argument);
        }
        if (type.equals("call_expression")) {
            TSNode function = current.getChildByFieldName("function");
            if (!function.isNull()
                    && function.getType().equals("identifier")
                    && text(function, source).equals("eval")) mutations.add(current);
        }
        for (int i = 0; i < current.getNamedChildCount(); i++)
            collectScope(root, current.getNamedChild(i), name, declarations, mutations, source);
    }

    private static void collectPattern(TSNode node, String name, List<TSNode> result, String source) {
        if (node.getType().equals("identifier")) {
            if (text(node, source).equals(name)) result.add(node);
            return;
        }
        for (int i = 0; i < node.getNamedChildCount(); i++) collectPattern(node.getNamedChild(i), name, result, source);
        if (node.getType().equals("object_pattern") && text(node, source).contains(name)) {
            walk(node, child -> {
                if (child.getType().equals("identifier") && text(child, source).equals(name)) result.add(child);
            });
        }
    }

    private static boolean isScope(TSNode node) {
        return SCOPE_TYPES.contains(node.getType());
    }

    private static Definition enclosingDefinition(TSNode call, List<Definition> defs) {
        return defs.stream()
                .filter(d -> contains(d.ast, call))
                .max(Comparator.comparingInt(d -> d.node.startLine()))
                .orElse(null);
    }

    private static boolean contains(TSNode parent, TSNode child) {
        return parent.getStartByte() <= child.getStartByte() && parent.getEndByte() >= child.getEndByte();
    }

    private static Definition definition(TSNode ast, String source, String path) {
        String type = ast.getType();
        TSNode nameNode = ast.getChildByFieldName("name");
        if (nameNode.isNull() && type.equals("method_definition")) nameNode = ast.getChildByFieldName("property");
        if (nameNode.isNull()) return null;
        String name = text(nameNode, source);
        SymbolKind kind = type.equals("class_declaration")
                ? SymbolKind.CLASS
                : type.equals("method_definition") ? SymbolKind.METHOD : SymbolKind.FUNCTION;
        if (kind == SymbolKind.METHOD) {
            TSNode parent = ast.getParent();
            while (!parent.isNull() && !parent.getType().equals("class_declaration")) parent = parent.getParent();
            if (!parent.isNull() && !parent.getChildByFieldName("name").isNull())
                name = text(parent.getChildByFieldName("name"), source) + "." + name;
        }
        return new Definition(name, node(path, kind, name, ast, source), ast);
    }

    private static GraphNode node(String path, SymbolKind kind, String name, TSNode ast, String source) {
        int endLine = kind == SymbolKind.FILE
                ? Math.max(line(ast), ast.getEndPoint().getRow())
                : ast.getEndPoint().getRow() + 1;
        return new GraphNode(
                StableNodeId.create(
                        "", "", path, kind, name, line(ast), ast.getStartPoint().getColumn()),
                kind,
                name,
                path,
                line(ast),
                endLine,
                ast.getStartPoint().getColumn(),
                ast.getEndPoint().getColumn());
    }

    private static String importedModule(TSNode n, String source) {
        for (int i = 0; i < n.getNamedChildCount(); i++) {
            TSNode c = n.getNamedChild(i);
            if (c.getType().equals("string")) return unquote(text(c, source));
        }
        return null;
    }

    private static List<GraphExport> exportNames(TSNode n, String source, String path) {
        List<GraphExport> result = new ArrayList<>();
        walk(n, c -> {
            if (c.getType().equals("export_specifier")) {
                TSNode alias = c.getChildByFieldName("alias");
                TSNode named = alias.isNull() ? c.getChildByFieldName("name") : alias;
                if (!named.isNull()) result.add(new GraphExport(text(named, source), location(path, named)));
            } else if ((c.getType().equals("function_declaration")
                            || c.getType().equals("class_declaration"))
                    && c.getParent().getType().equals("export_statement")) {
                TSNode named = c.getChildByFieldName("name");
                if (!named.isNull()) result.add(new GraphExport(text(named, source), location(path, named)));
            }
        });
        return result;
    }

    private static SourceLocation location(String path, TSNode n) {
        return new SourceLocation(
                path,
                line(n),
                n.getStartPoint().getColumn(),
                n.getEndPoint().getRow() + 1,
                n.getEndPoint().getColumn());
    }

    private static String unquote(String value) {
        return value.length() > 1 ? value.substring(1, value.length() - 1) : value;
    }

    private static int line(TSNode n) {
        return n.getStartPoint().getRow() + 1;
    }

    private static String text(TSNode n, String source) {
        if (n.isNull() || source == null) return "";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, n.getStartByte(), n.getEndByte() - n.getStartByte(), StandardCharsets.UTF_8);
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static ParsedFile empty(String path, GraphWarning warning) {
        return new ParsedFile(path, List.of(), List.of(), List.of(warning), List.of(), List.of(), List.of());
    }

    private static void walk(TSNode node, java.util.function.Consumer<TSNode> consumer) {
        consumer.accept(node);
        for (int i = 0; i < node.getNamedChildCount(); i++) walk(node.getNamedChild(i), consumer);
    }

    private record Definition(String name, GraphNode node, TSNode ast) {}

    private record ScopeBinding(Definition definition, boolean mutated) {}
}
