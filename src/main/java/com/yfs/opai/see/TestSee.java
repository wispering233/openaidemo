package com.yfs.opai.see;

import cn.hutool.http.ContentType;
import com.alibaba.fastjson2.JSON;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import com.yfs.opai.Interceptor.OpenAIApiKeyInterceptor;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TestSee {
    public static void main(String[] args) {
         List<Message> messages = new ArrayList<>();

        while (true) {
            String prompt = getInput("\nYou:\n");
            messages.add(new Message(Message.Role.USER.getName(), prompt,"1111"));
            ChatCompletion q = ChatCompletion.builder()
                    .model("gpt-3.5-turbo")
                    .messages(messages)
                    .stream(true)
                    .build();
            System.out.println("AI: ");
            //卡住
            CountDownLatch countDownLatch = new CountDownLatch(1);

            ChatConsoleEventSourceListener listener = new ChatConsoleEventSourceListener() {
                @Override
                public void onError(Throwable throwable, String response) {
                    throwable.printStackTrace();
                    System.out.println("fail");
                    countDownLatch.countDown();
                }
            };

            listener.setOnComplate(msg -> {
                countDownLatch.countDown();
            });
            Request request = new Request.Builder()
                    .url("http://38.6.178.54:9999/v1/chat/completions")
                    .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), JSON.toJSONString(q)))
                    .build();

           OkHttpClient  httpClient = new OkHttpClient.Builder()
                    .addInterceptor(new OpenAIApiKeyInterceptor("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"))
                    .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                    .readTimeout(Duration.ofSeconds(30))
                    .build();
            EventSource.Factory factory = EventSources.createFactory(httpClient);

            EventSource eventSource = factory.newEventSource(request, listener);
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getInput(String s) {
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
}
