package com.springlab.service;

import com.springlab.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    @Autowired
    private Environment environment;

    public void inspect() {
    System.out.println(
        "MessageService instance: " +
        System.identityHashCode(this)
    );

    System.out.println(
        "Environment: " + environment
    );
}

    public Message getMessage() {
        String text = environment.getProperty(
            "app.message",
            "Default message"
        );

        return new Message(text);
    }
}
