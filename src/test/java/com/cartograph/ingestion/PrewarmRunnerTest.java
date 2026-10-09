package com.cartograph.ingestion;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cartograph.application.IndexRepositoryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PrewarmRunnerTest {
    private IndexRepositoryService service = mock(IndexRepositoryService.class);
    private PrewarmProperties properties = new PrewarmProperties();

    @Test
    void disabledByDefaultDoesNothing() {
        new PrewarmRunner(service, properties).run(null);
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void indexesEveryConfiguredRepositorySequentiallyAndSurvivesFailures() {
        properties.setEnabled(true);
        properties.setRepositories(java.util.List.of("acme/one", "acme/broken", "acme/two"));
        Mockito.when(service.index(anyString()))
                .thenReturn(Mockito.mock(com.cartograph.graph.model.GraphSnapshot.class))
                .thenThrow(new RuntimeException("github down"))
                .thenReturn(Mockito.mock(com.cartograph.graph.model.GraphSnapshot.class));

        new PrewarmRunner(service, properties).run(null);

        verify(service, Mockito.after(1000).never()).index("https://github.com/never-called");
        Mockito.verify(service).index("https://github.com/acme/one");
        Mockito.verify(service).index("https://github.com/acme/broken");
        Mockito.verify(service).index("https://github.com/acme/two");
    }
}
