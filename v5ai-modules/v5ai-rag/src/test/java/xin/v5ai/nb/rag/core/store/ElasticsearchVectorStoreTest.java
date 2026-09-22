package xin.v5ai.nb.rag.core.store;

import org.apache.http.StatusLine;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xin.v5ai.nb.common.elasticsearch.domain.ElasticsearchVectorConfigDO;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ElasticsearchVectorStore} 的 Mockito 单测：mock {@link RestClient}，
 * 覆盖「删除不存在的索引幂等跳过」（与 Milvus 同源根因回归），以及索引存在时的删除代理。
 */
class ElasticsearchVectorStoreTest {

    private RestClient client;
    private ElasticsearchVectorStore store;

    @BeforeEach
    void setUp() {
        client = mock(RestClient.class);
        var config = new ElasticsearchVectorConfigDO();
        config.setIndexPrefix("v5ai_rag_vector");
        store = new ElasticsearchVectorStore(client, config);
    }

    private static Response response(int status) {
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(status);
        Response response = mock(Response.class);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }

    @Test
    void deleteByDocumentIdSkipsWhenIndexMissing() throws IOException {
        var response = response(404);
        when(client.performRequest(any(Request.class))).thenReturn(response);

        store.deleteByDocumentId(2L, 10L);

        var captor = ArgumentCaptor.forClass(Request.class);
        verify(client, times(1)).performRequest(captor.capture());
        assertThat(captor.getValue().getMethod()).isEqualTo("HEAD");
    }

    @Test
    void deleteByVectorIdSkipsWhenIndexMissing() throws IOException {
        var response = response(404);
        when(client.performRequest(any(Request.class))).thenReturn(response);

        store.deleteByVectorId(2L, "v1");

        var captor = ArgumentCaptor.forClass(Request.class);
        verify(client, times(1)).performRequest(captor.capture());
        assertThat(captor.getValue().getMethod()).isEqualTo("HEAD");
    }

    @Test
    void deleteByDocumentIdDeletesByQueryWhenIndexExists() throws IOException {
        var response = response(200);
        when(client.performRequest(any(Request.class))).thenReturn(response);

        store.deleteByDocumentId(2L, 10L);

        var captor = ArgumentCaptor.forClass(Request.class);
        verify(client, times(2)).performRequest(captor.capture());
        var requests = captor.getAllValues();
        assertThat(requests.get(0).getMethod()).isEqualTo("HEAD");
        assertThat(requests.get(1).getMethod()).isEqualTo("POST");
        assertThat(requests.get(1).getEndpoint()).isEqualTo("/v5ai_rag_vector_2/_delete_by_query");
    }

    @Test
    void deleteByVectorIdDeletesDocWhenIndexExists() throws IOException {
        var response = response(200);
        when(client.performRequest(any(Request.class))).thenReturn(response);

        store.deleteByVectorId(2L, "v1");

        var captor = ArgumentCaptor.forClass(Request.class);
        verify(client, times(2)).performRequest(captor.capture());
        var requests = captor.getAllValues();
        assertThat(requests.get(0).getMethod()).isEqualTo("HEAD");
        assertThat(requests.get(1).getMethod()).isEqualTo("DELETE");
        assertThat(requests.get(1).getEndpoint()).isEqualTo("/v5ai_rag_vector_2/_doc/v1");
    }
}
