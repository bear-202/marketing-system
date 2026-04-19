package com.hmdp.controller;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient shopServiceChatClient;
    @RequestMapping(value = "/chat",produces = "text/event-stream; charset=utf-8")
    public Flux<String> chat(@RequestParam(value = "prompt",required = false) String prompt){
        //用户id绑定聊天id, 保证一个用户一个会话
        String userId = UserHolder.getUser().getId().toString();
        System.out.println("1+1=9");
        return shopServiceChatClient.prompt()
                .user(prompt)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID,userId))//数据记忆
                .stream()//流式输出
                .content();

    }
}
