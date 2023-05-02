package com.example.chatting.controller;

import com.example.chatting.dto.ChatDTO;
import com.example.chatting.dao.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;

/*
- @MessageMapping : 이 어노테이션은 Stomp 에서 들어오는 message 를 서버에서 발송(pub) 한 메시지가 도착하는 엔드포인트이다.
여기서 "/chat/enterUser" 로 되어있지만 실제로는 앞에 "/pub" 가 생략되어있다라고 생각하면 된다.
즉 클라이언트가 "/pub/chat/enterUser"로 메시지를 발송하면 @MessageMapping 에 의해서 아래의 해당 어노테이션이 달린 메서드가 실행된다.

- convertAndSend() : 이 메서드는 매개변수로 각각 메시지의 도착 지점과 객체를 넣어준다.
이를 통해서 도착 지점 즉 sub 되는 지점으로 인자로 들어온 객체를 Message 객체로 변환해서
해당 도작지점을 sub 하고 있는 모든 사용자에게 메시지를 보내주게 된다.
 */

// 채팅을 수신(sub)하고, 송신(pub) 하기 위한 Controller
@Slf4j
@RequiredArgsConstructor
@Controller
public class ChatController {

    private final SimpMessageSendingOperations template;
    private final ChatRepository repository;

    // MessageMapping 을 통해 webSocket 로 들어오는 메시지를 발신 처리한다.
    // 이때 클라이언트에서는 /pub/chat/message 로 요청하게 되고 이것을 controller 가 받아서 처리한다.
    // 처리가 완료되면 /sub/chat/room/roomId 로 메시지가 전송된다.

    @MessageMapping("/chat/enterUser")
    /*
       - @Payload: 스프링 프레임워크에서 사요오디는 애노테이션으로, 메소드 파라미터가 WebSocket 메시지의 페이로드를 의미함을 나타낸다.

       - SimpMessageHeaderAccessor는 스프링 프레임워크에서 사용되는 WebSocket 메시지의 헤더 정보를 읽거나 쓰기 위한 클래스이다.
       이 클래스를 사용하면 WebSocket 메시지의 헤더 정보에 접근하여 메시지를 더욱 효과적으로 처리할 수 있다.
     */
    public void enterUser(@Payload ChatDTO chat, SimpMessageHeaderAccessor headerAccessor) {

        // 채팅방 유저+1
        repository.plusUserCnt(chat.getRoomId());

        // 채팅방에 유저 추가 및 UserUUID 반환
        String userUUID = repository.addUser(chat.getRoomId(), chat.getSender());

        // 반환 결과를 socket session 에 userUUID 로 저장
        headerAccessor.getSessionAttributes().put("userUUID", userUUID);
        headerAccessor.getSessionAttributes().put("roomId", chat.getRoomId());

        chat.setMessage(chat.getSender() + " 님 입장!!");

        // Spring의 WebSocket을 사용하여 클라이언트에게 메시지를 전송하는 코드
        // template.convertAndSend(destination, payload) 메서드를 사용하여 메시지 전송
        // WebSocket 클라이언트에서 /sub/chat/room/{roomId}으로 구독하고 있으면, 해당 채팅방에 새로운 메시지가 도착할때마다 모든 클라에 전달
        template.convertAndSend("/sub/chat/room/" + chat.getRoomId(), chat);

    }

    // 해당 유저
    @MessageMapping("/chat/sendMessage")
    public void sendMessage(@Payload ChatDTO chat) {
        log.info("CHAT {}", chat);
        chat.setMessage(chat.getMessage());
        template.convertAndSend("/sub/chat/room/" + chat.getRoomId(), chat);
    }

    // 유저 퇴장 시에는 EventListener 을 통해서 유저 퇴장을 확인
    // @EventListener: 스프링 프레임워크에서 이벤트 처리를 위한 애노테이션
    @EventListener
    // SessionDisconnectEvent: WebSokcet 연결이 끊어졌을 때 발생하는 이벤트
    public void webSocketDisconnectListener(SessionDisconnectEvent event) {
        log.info("DisConnEvent {}", event);

        // StompHeaderAccessor: STOMP 프로토콜에서 사용되는 메시지 헤더 정보를 읽거나 쓰기 위한 클래스
        // wrap() 메소드를 이용하여 SessionDisconnectEvent에서 추출한 Message 객체를 StompHeaderAccessor로 변환할 수 있다.
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // stomp 세션에 있던 uuid 와 roomId 를 확인해서 채팅방 유저 리스트와 room 에서 해당 유저를 삭제
        String userUUID = (String) headerAccessor.getSessionAttributes().get("userUUID");
        String roomId = (String) headerAccessor.getSessionAttributes().get("roomId");

        log.info("headAccessor {}", headerAccessor);

        // 채팅방 유저 -1
        repository.minusUserCnt(roomId);

        // 채팅방 유저 리스트에서 UUID 유저 닉네임 조회 및 리스트에서 유저 삭제
        String username = repository.getUserName(roomId, userUUID);
        repository.delUser(roomId, userUUID);

        if (username != null) {
            log.info("User Disconnected : " + username);

            // builder 어노테이션 활용
            ChatDTO chat = ChatDTO.builder()
                    .type(ChatDTO.MessageType.LEAVE)
                    .sender(username)
                    .message(username + " 님 퇴장!!")
                    .build();

            template.convertAndSend("/sub/chat/room/" + roomId, chat);
        }
    }

    // 채팅에 참여한 유저 리스트 반환
    @GetMapping("/chat/userlist")
    @ResponseBody
    public ArrayList<String> userList(String roomId) {

        return repository.getUserList(roomId);
    }

    // 채팅에 참여한 유저 닉네임 중복 확인
    @GetMapping("/chat/duplicateName")
    @ResponseBody
    public String isDuplicateName(@RequestParam("roomId") String roomId, @RequestParam("username") String username) {

        // 유저 이름 확인
        String userName = repository.isDuplicateName(roomId, username);
        log.info("동작확인 {}", userName);

        return userName;
    }
}
