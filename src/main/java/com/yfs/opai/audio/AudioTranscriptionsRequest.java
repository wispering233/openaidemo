package com.yfs.opai.audio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AudioTranscriptionsRequest {
    private String file;
    private String model;
    private String prompt;
    @JsonProperty("response_format")
    private String responseFormat;
    private String temperature;
    private String language;


}
