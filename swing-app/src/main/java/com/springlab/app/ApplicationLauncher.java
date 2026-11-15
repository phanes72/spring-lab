package com.springlab.app;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ApplicationLauncher {

    public static void main(String[] args) {

        try (ClassPathXmlApplicationContext context =
                new ClassPathXmlApplicationContext(
                    "applicationContext.xml")) {

            System.out.println("Spring initialized!");
        }
    }
}
