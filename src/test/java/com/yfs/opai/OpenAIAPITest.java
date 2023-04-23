package com.yfs.opai;

import cn.hutool.http.ContentType;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.theokanning.openai.AuthenticationInterceptor;
import com.theokanning.openai.OpenAiApi;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import com.theokanning.openai.edit.EditRequest;
import com.theokanning.openai.edit.EditResult;
import com.theokanning.openai.image.*;
import com.theokanning.openai.service.OpenAiService;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import com.unfbx.chatgpt.entity.completions.Completion;
import com.yfs.opai.Interceptor.OpenAIApiKeyInterceptor;
import com.yfs.opai.audio.AudioTranscriptionsRequest;
import com.yfs.opai.audio.AudioTranscriptionsResult;
import com.yfs.opai.see.ConsoleEventSourceListener;
import lombok.SneakyThrows;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@SpringBootTest
public class OpenAIAPITest {
    private static OpenAiService openAiService;
    private static OkHttpClient httpClient;

    @BeforeAll
    public static void beforeAll() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        httpClient = new OkHttpClient.Builder()
                .addInterceptor(new OpenAIApiKeyInterceptor("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"))
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                .readTimeout(Duration.ofSeconds(30))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://38.6.178.54:9999")
                .client(httpClient)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        OpenAiApi openAiApi = retrofit.create(OpenAiApi.class);
        openAiService = new OpenAiService(openAiApi);
    }

    @Test
    public void completionTest() {

        HashMap<String, Integer> map = new HashMap<>();

        CompletionRequest completionRequest = CompletionRequest.builder()
                .model("text-davinci-003")
                .prompt("如果新开一家火锅店，请帮我给这家店提出5个中文名字")
                .suffix("不要火锅")
                .maxTokens(128)
                .topP(0D)
                .stream(Boolean.FALSE)
                .n(1)
                .bestOf(2)
//                .logprobs(4)
                .logitBias(map)
                .build();

        CompletionResult completionResult = openAiService.createCompletion(completionRequest);
        List<CompletionChoice> choices = completionResult.getChoices();
        System.out.println(JSONUtil.toJsonStr(choices));
    }

    @SneakyThrows
    @Test
    public void testStreaming(){
        Completion q = Completion.builder()
                .prompt("请写一封信，安慰因为而在课堂上拉肚子被同学们嘲笑的乔治同学")
                .stream(true)
                .build();
        Request request = new Request.Builder()
                .url("http://38.6.178.54:9999/v1/completions")
                .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), JSON.toJSONString(q)))
                .build();
        EventSource.Factory factory = EventSources.createFactory(httpClient);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        ConsoleEventSourceListener listener = new ConsoleEventSourceListener() {
            @Override
            public void onError(Throwable throwable, String response) {
                throwable.printStackTrace();
                countDownLatch.countDown();
            }
        };

        listener.setOnComplate(msg -> {
            countDownLatch.countDown();
        });

        factory.newEventSource(request, listener);
        countDownLatch.await();

    }


    @Test
    public void testChat(){
        final List<Message> messages = new ArrayList<>();


        ChatCompletion q = ChatCompletion.builder()
                .messages(messages)
                .stream(true)
                .build();

        while (true) {
            String prompt = getInput("\nYou:\n");
            messages.add(new Message(Message.Role.SYSTEM.getName(), prompt,"人类"));
            System.out.println("AI: ");
            //卡住
            CountDownLatch countDownLatch = new CountDownLatch(1);

            ConsoleEventSourceListener listener = new ConsoleEventSourceListener() {
                @Override
                public void onError(Throwable throwable, String response) {
                    throwable.printStackTrace();
                    countDownLatch.countDown();
                }
            };

            listener.setOnComplate(msg -> {
                countDownLatch.countDown();
            });
            Request request = new Request.Builder()
                    .url("http://38.6.178.54:9999/v1/completions")
                    .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), JSON.toJSONString(q)))
                    .build();

            EventSource.Factory factory = EventSources.createFactory(httpClient);

            factory.newEventSource(request, listener);
            String lastMessage = listener.getLastMessage();

            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private String getInput(String s) {
        System.out.print(s);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<String> lines = new ArrayList<>();
        String line;
        try {
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines.stream().collect(Collectors.joining("\n"));
    }

    @Test
    public void testEdit(){
        EditRequest request = EditRequest.builder()
                .model("text-davinci-edit-001")
                .input("What day of the wek is is?")
                .instruction("the Fix  spell")
                .build();

        EditResult result = openAiService.createEdit(request);
        System.out.println();
    }

    @Test
    public void testImagesGeneration(){
        CreateImageRequest createImageRequest = CreateImageRequest.builder()
                .prompt("中分头，背带裤")
                .n(3)
                .size("256x256")
                .user("testing")
                .build();
        List<Image> images = openAiService.createImage(createImageRequest).getData();
        System.out.println();
    }

    @Test
    public void testImagesEdit(){
        CreateImageEditRequest createImageRequest = CreateImageEditRequest.builder()
                .prompt("a red penguin with a red hat")
                .responseFormat("url")
                .size("256x256")
                .user("testing")
                .n(2)
                .build();
        ImageResult result = openAiService.createImageEdit(createImageRequest, "src/test/resources/penguin.png",
                "src/test/resources/mask.png");
        List<Image> images = result.getData();
        System.out.println();
    }

    @Test
    public void testImagesVariations(){
        CreateImageVariationRequest createImageVariationRequest = CreateImageVariationRequest.builder()
                .responseFormat("url")
                .size("256x256")
                .user("testing")
                .n(1)
                .build();

        List<Image> images = openAiService.createImageVariation(createImageVariationRequest, "src/test/resources/penguin.png").getData();
        System.out.println(images);
    }

    @SneakyThrows
    @Test
    public void testAudioTranscriptions(){
        AudioTranscriptionsRequest request = AudioTranscriptionsRequest.builder()
                .model("whisper-1")
                .prompt("翻译张国荣唱的这首粤语歌")
                .build();
        java.io.File file = new java.io.File("src/test/resources/千千阕歌 .mp3");
        RequestBody imageBody = RequestBody.create(MediaType.parse("file"), file);
        MultipartBody.Builder builder = (new MultipartBody.Builder())
                .setType(MediaType.get("multipart/form-data"))
                .addFormDataPart("model", request.getModel())
                .addFormDataPart("file", file.getName(), imageBody);
        if (request.getPrompt() != null) {
            builder.addFormDataPart("prompt", request.getPrompt());
        }
        if (request.getResponseFormat() != null) {
            builder.addFormDataPart("response_format", request.getResponseFormat());
        }
        if (request.getTemperature() != null) {
            builder.addFormDataPart("temperature", request.getTemperature());
        }
        if (request.getLanguage() != null) {
            builder.addFormDataPart("language", request.getLanguage());
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new OpenAIApiKeyInterceptor("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"))
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                .readTimeout(Duration.ofSeconds(30))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://38.6.178.54:9999")
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        CustomApiService customApiService = retrofit.create(CustomApiService.class);

        AudioTranscriptionsResult result =
                (AudioTranscriptionsResult)OpenAiService.execute(customApiService.createAudioTranscriptions(builder.build()));
        System.out.println(result.getText());

    }


}
