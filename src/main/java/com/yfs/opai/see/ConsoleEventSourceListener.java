package com.yfs.opai.see;


import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

import java.util.Objects;

/**
 * 描述： sse
 *
 * @author https:www.unfbx.com
 * 2023-02-28
 */
@Slf4j
public class ConsoleEventSourceListener extends AbstractStreamListener {

    @Override
    public void onMsg(String message) {
        System.out.print(message);
    }

    @Override
    public void onError(Throwable throwable, String response) {

    }


}
