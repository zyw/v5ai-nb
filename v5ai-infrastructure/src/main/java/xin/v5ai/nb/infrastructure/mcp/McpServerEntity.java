package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("v5ai_mcp_server")
public class McpServerEntity {
    @TableId
    private Long id;
    private String name;
    private String transportType;
    private String endpoint;
    private String argsJson;
    private String headersCiphertext;
    private String envCiphertext;
    private Integer timeoutSeconds;
    private String status;
    private String lastTestStatus;
    private String lastTestMessage;
    private Instant lastTestedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getArgsJson() { return argsJson; }
    public void setArgsJson(String argsJson) { this.argsJson = argsJson; }
    public String getHeadersCiphertext() { return headersCiphertext; }
    public void setHeadersCiphertext(String headersCiphertext) { this.headersCiphertext = headersCiphertext; }
    public String getEnvCiphertext() { return envCiphertext; }
    public void setEnvCiphertext(String envCiphertext) { this.envCiphertext = envCiphertext; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastTestStatus() { return lastTestStatus; }
    public void setLastTestStatus(String lastTestStatus) { this.lastTestStatus = lastTestStatus; }
    public String getLastTestMessage() { return lastTestMessage; }
    public void setLastTestMessage(String lastTestMessage) { this.lastTestMessage = lastTestMessage; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public void setLastTestedAt(Instant lastTestedAt) { this.lastTestedAt = lastTestedAt; }
}
