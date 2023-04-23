package com.yfs.opai.controller;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import com.unfbx.chatgpt.interceptor.OpenAILogger;
import com.unfbx.chatgpt.sse.ConsoleEventSourceListener;
import com.yfs.opai.controller.model.WeChatMessage;
import com.yfs.opai.controller.model.WeChatResponseData;
import com.yfs.opai.utils.RedisUtil;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


@RequestMapping("wechat")
@RestController
@Slf4j
public class WechatController {
    OpenAiService openAiService = new OpenAiService("sk-****");
    @Autowired
    private RedisUtil redisUtil;


    @GetMapping("save")
    public String save(String fromUserName, String content) {
        String roleType;
        final List<ChatMessage> messages = new ArrayList<>();
        if (!redisUtil.hasKey(fromUserName)) {
            roleType = ChatMessageRole.SYSTEM.value();
        } else {
            roleType = ChatMessageRole.USER.value();
            List<String> range = redisUtil.range(fromUserName, 0, redisUtil.listLength(fromUserName));
            for (String s : range) {
                JSON json = JSONUtil.parse(s);
                messages.add(new ChatMessage(json.getByPath("role", String.class), json.getByPath("content", String.class)));
            }
        }
        ChatMessage newQuestion = new ChatMessage(roleType, content);
        messages.add(newQuestion);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .n(1)
                .maxTokens(1000)
                .logitBias(new HashMap<>())
                .build();
        List<ChatCompletionChoice> choices = openAiService.createChatCompletion(chatCompletionRequest).getChoices();
        String resultContent = choices.get(0).getMessage().getContent();
        redisUtil.rightPush(fromUserName, JSONUtil.toJsonStr(newQuestion));
        redisUtil.rightPush(fromUserName, JSONUtil.toJsonStr(new ChatMessage(ChatMessageRole.ASSISTANT.value(), resultContent)));
        List<String> range = redisUtil.range(fromUserName, 0, redisUtil.listLength(fromUserName));
        redisUtil.expire(fromUserName, 30 * 60);
        return JSONUtil.toJsonStr(range);
    }


    @SneakyThrows
    @RequestMapping("readiness")
    public String wechat(String content) {

        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.USER.value(), content);
        messages.add(systemMessage);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .n(1)
                .maxTokens(100)
                .logitBias(new HashMap<>())
                .build();
        List<ChatCompletionChoice> choices = openAiService.createChatCompletion(chatCompletionRequest).getChoices();
        return choices.get(0).getMessage().getContent();

    }


    @SneakyThrows
    @RequestMapping(value = "/cat", produces = MediaType.APPLICATION_XML_VALUE)
    public WeChatResponseData cat(HttpServletRequest httpServletRequest) {
        WeChatResponseData weChatResponseData = new WeChatResponseData();
        weChatResponseData.setCtime(LocalTime.now().toEpochSecond(LocalDate.now(), ZoneOffset.of("+8")));
        weChatResponseData.setMsgType("text");
        weChatResponseData.setFromUserName("gh_9c1f00a5353b");
        String fromUserName = "";
        try (ServletInputStream inputStream = httpServletRequest.getInputStream()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);
            NodeList nodeList = doc.getElementsByTagName("xml").item(0).getChildNodes();
            String toUserName = nodeList.item(1).getTextContent();
            fromUserName = nodeList.item(3).getTextContent();
            String createTime = nodeList.item(5).getTextContent();
            String msgType = nodeList.item(7).getTextContent();
            String content = nodeList.item(9).getTextContent();
            String msgId = nodeList.item(11).getTextContent();
            String roleType;
            final List<ChatMessage> messages = new ArrayList<>();
            if (!redisUtil.hasKey(fromUserName)) {
                roleType = ChatMessageRole.SYSTEM.value();
            } else {
                roleType = ChatMessageRole.USER.value();
                List<String> range = redisUtil.range(fromUserName, 0, redisUtil.listLength(fromUserName));
                for (String s : range) {
                    JSON json = JSONUtil.parse(s);
                    messages.add(new ChatMessage(json.getByPath("role", String.class), json.getByPath("content", String.class)));
                }
            }
            ChatMessage newQuestion = new ChatMessage(roleType, content);
            messages.add(newQuestion);


            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                    .builder()
                    .model("gpt-3.5-turbo")
                    .messages(messages)
                    .n(1)
                    .maxTokens(250)
                    .logitBias(new HashMap<>())
                    .build();
            List<ChatCompletionChoice> choices = openAiService.createChatCompletion(chatCompletionRequest).getChoices();
            String resultContent = choices.get(0).getMessage().getContent();
            weChatResponseData.setContent(resultContent);
            weChatResponseData.setToUserName(fromUserName);
            redisUtil.rightPush(fromUserName, JSONUtil.toJsonStr(newQuestion));
            redisUtil.rightPush(fromUserName, JSONUtil.toJsonStr(new ChatMessage(ChatMessageRole.ASSISTANT.value(), resultContent)));
            redisUtil.expire(fromUserName, 30 * 60);
            return weChatResponseData;
        } catch (Exception e) {
            log.warn("失敗{},{}", e, fromUserName);
            weChatResponseData.setContent("ok");
            return weChatResponseData;
        }
    }


    public static void main(String[] args) {
                //国内访问需要做代理，国外服务器不需要
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("38.6.178.54", 6666));
                HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(new OpenAILogger());
                //！！！！千万别再生产或者测试环境打开BODY级别日志！！！！
                //！！！生产或者测试环境建议设置为这三种级别：NONE,BASIC,HEADERS,！！！
                httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
                OkHttpClient okHttpClient = new OkHttpClient
                        .Builder()
                        .proxy(proxy)//自定义代理
                        .addInterceptor(httpLoggingInterceptor)//自定义日志
                        .connectTimeout(30, TimeUnit.SECONDS)//自定义超时时间
                        .writeTimeout(30, TimeUnit.SECONDS)//自定义超时时间
                        .readTimeout(30, TimeUnit.SECONDS)//自定义超时时间
                        .build();
                OpenAiStreamClient client = OpenAiStreamClient.builder()
                        .apiKey(Arrays.asList("sk->"))
                        //自定义key的获取策略：默认KeyRandomStrategy
                        //.keyStrategy(new KeyRandomStrategy())
//                        .keyStrategy(new FirstKeyStrategy())
                        .okHttpClient(okHttpClient)
                        //自己做了代理就传代理地址，没有可不不传
//                .apiHost("https://自己代理的服务器地址/")
                        .build();

//聊天模型：gpt-3.5
        ConsoleEventSourceListener eventSourceListener = new ConsoleEventSourceListener();
        Message message = Message.builder().role(Message.Role.USER).content("你好啊我的伙伴！").build();
        ChatCompletion chatCompletion = ChatCompletion.builder().messages(Arrays.asList(message)).build();
        client.streamChatCompletion(chatCompletion, eventSourceListener);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
