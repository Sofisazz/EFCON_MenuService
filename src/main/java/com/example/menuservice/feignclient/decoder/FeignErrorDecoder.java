package com.example.menuservice.feignclient.decoder;


import com.example.menuservice.exceptions.MissingException;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FeignErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {

        if (response.status() >= 400 && response.status() <= 499) {
            String responseBody;

            try {
                responseBody = new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new MissingException(responseBody);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return defaultErrorDecoder.decode(methodKey, response);
    }
}
