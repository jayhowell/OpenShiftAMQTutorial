package com.amq.jms.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;


@EnableJms
@SpringBootApplication
public class SpringbootAmqJmsClientApplication  implements CommandLineRunner {
	
	@Autowired
    private JmsTemplate jmsTemplate;

	public static void main(String[] args) {
		SpringApplication.run(SpringbootAmqJmsClientApplication.class, args);
	}
	
	@Override
    public void run(String... strings) throws Exception {
        sendMessage("Hello World!");
        sendMessage("Hello World2!");
        sendMessage("Hello World3!");
        sendMessage("Hello World4!");
        sendMessage("Hello World5!");
        sendMessage("Hello World6!");
    }
	
    public void sendMessage(String text) {
        System.out.println(String.format("Sending '%s'", text));
        this.jmsTemplate.convertAndSend("myAddress0", text);
    }

    @JmsListener(destination = "myAddress0")
    public void receiveMessage(String text) {
        System.out.println(String.format("Received '%s'", text));
    }

}
