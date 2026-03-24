package com.hmdp.config;


import com.hmdp.constant.AIConstants;
import com.hmdp.utils.ShopInfoServeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class aiConfig {


    @Bean
    public ChatMemory chatMemory(){
        return MessageWindowChatMemory.builder().maxMessages(20).build();
    }

    @Bean("shopServiceChatClient")
    public ChatClient shopServiceChatClient(OpenAiChatModel openAiChatModel, ChatMemory chatMemory , ShopInfoServeTools shopInfoServeTools){
        return ChatClient
                .builder(openAiChatModel)
                .defaultSystem(AIConstants.CUSTOMERS_SERVER_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(shopInfoServeTools)
                .build();
    }
}
