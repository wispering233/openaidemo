package com.yfs.opai;

import com.yfs.opai.audio.AudioTranscriptionsResult;
import io.reactivex.Single;
import okhttp3.RequestBody;
import retrofit2.http.Body;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Multipart;
import retrofit2.http.POST;

public interface CustomApiService {

    @POST("/v1/audio/transcriptions")
    Single<AudioTranscriptionsResult> createAudioTranscriptions(@Body RequestBody var1);
}
