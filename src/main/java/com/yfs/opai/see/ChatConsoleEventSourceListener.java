package com.yfs.opai.see;

import com.alibaba.fastjson2.JSON;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionResult;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.unfbx.chatgpt.entity.chat.ChatChoice;
import com.unfbx.chatgpt.entity.chat.ChatCompletionResponse;
import okhttp3.sse.EventSource;

import java.util.List;

public class ChatConsoleEventSourceListener extends AbstractStreamListener{
    @Override
    public void onMsg(String message) {
        System.out.print(message);
    }

    @Override
    public void onError(Throwable throwable, String response) {

    }

    public void onEvent(EventSource eventSource, String id, String type, String data) {
        if (data.equals("[DONE]")) {
            onComplate.accept(lastMessage);
            return;
        }

        ChatCompletionResult response = JSON.parseObject(data, ChatCompletionResult.class);
        // 读取Json
        List<ChatCompletionChoice> choices = response.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        String text = choices.get(0).getMessage().getContent();
        if (text != null) {
            lastMessage += text;

            onMsg(text);

        }

    }
}
