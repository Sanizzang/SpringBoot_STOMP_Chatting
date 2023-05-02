'use strict';

// document.write("<script src='jquery-3.6.1.js'></script>")
document.write("<script\n" +
    "  src=\"https://code.jquery.com/jquery-3.6.1.min.js\"\n" +
    "  integrity=\"sha256-o88AwQnZB+VDvE9tvIXrMQaPlFFSUTR+nldQm1LuPXQ=\"\n" +
    "  crossorigin=\"anonymous\"></script>")


var usernamePage = document.querySelector('#username-page');
var chatPage = document.querySelector('#chat-page');
var usernameForm = document.querySelector('#usernameForm');
var messageForm = document.querySelector('#messageForm');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#messageArea');
var connectingElement = document.querySelector('.connecting');

var stompClient = null;
var username = null;

var colors = [
    '#2196F3', '#32c787', '#00BCD4', '#ff5652',
    '#ffc107', '#ff85af', '#FF9800', '#39bbb0'
];

// roomId 파라미터 가져오기
const url = new URL(location.href).searchParams;
const roomId = url.get('roomId');

// WebSocket 연결 수행
function connect(event) {
    // name 이라는 ID를 가진 입력 필드에 입력된 값을 가져온다.
    username = document.querySelector('#name').value.trim();

    // username 중복 확인
    isDuplicateName();

    // usernamePage 에 hidden 속성 추가해서 가리고
    // chatPage 를 등장시킴
    usernamePage.classList.add('hidden');
    chatPage.classList.remove('hidden');

    // /ws-stomp 엔드포인트에 대한 SockJS 객체 생성
    var socket = new SockJS('/ws-stomp');
    // 생성된 SockJS 객체를 이용하여 Stomp 클라이언트 객체를 생성
    stompClient = Stomp.over(socket);

    // WebSocket 연결시도
    // onConnected 함수는 연결이 성공했을 때 호출되며,
    // onError 함수는 WebSocket 연결이 실패했을 때 호출
    stompClient.connect({}, onConnected, onError);

    // 폼 전송 이벤트를 취소, 이렇게 하면 페이지가 새로고침되지 않고, WebSocket 연결이 수행된다.
    event.preventDefault();
}

function onConnected() {

    // 현재 방('roomId')에 대한 채팅 메시지를 구독한다. 이때, onMessageReceived 함수는 새로운 채팅 메시지가 도착했을 때 호출된다.
    stompClient.subscribe('/sub/chat/room/' + roomId, onMessageReceived);

    // 새로운 사용자가 현재 방에 입장했음을 알리는 메시지를 서버로 보낸다.
    // 이때 /pub/chat/enterUser는 메시지를 보낼 엔드포인트 이며 JSON.stringfy({...})는 보낼 JSON 형식의 메시지 내용이다.
    stompClient.send("/pub/chat/enterUser",
        {},
        JSON.stringify({
            "roomId": roomId,
            "sender": username,
            "type": 'ENTER'
        })
    )

    connectingElement.classList.add('hidden');
}

// 유저 닉네임 중복 확인
function isDuplicateName() {

    $.ajax({
        type: "GET",
        url: "/chat/duplicateName",
        data: {
            "username": username,
            "roomId": roomId
        },
        success: function (data) {
            console.log("함수 동작 확인 : " + data);
            username = data;
        }
    })
}

// 유저 리스트 받기
// ajax 로 유저 리스트를 받으며 클라이언트가 입장/퇴장 했다는 문구가 나왔을 때마다 실행된다.
function getUserList() {
    // id가 list인 요소를 가져오는 jQuery 코드
    const $list = $("#list");

    $.ajax({
        type: "GET",
        url: "/chat/userlist",
        data: {
            "roomId": roomId
        },
        success: function (data) {
            var users = "";
            for (let i = 0; i < data.length; i++) {
                //console.log("data[i] : "+data[i]);
                users += "<li class='dropdown-item'>" + data[i] + "</li>"
            }
            // list 요소의 HTML 내용을 갱신
            $list.html(users);
        }
    })
}


function onError(error) {
    connectingElement.textContent = 'Could not connect to WebSocket server. Please refresh this page to try again!';
    connectingElement.style.color = 'red';
}

// 메시지 전송때는 JSON 형식으로 메시지를 전달한다.
function sendMessage(event) {
    // 메시지 입력 필드에 입련된 값을 가져온다.
    var messageContent = messageInput.value.trim();

    // 입련된 메시지가 있고, WebSocket 연결이 성공했을 때만 메시지를 전송
    if (messageContent && stompClient) {
        // 전송할 메시지를 구성하는 JSON 객체
        var chatMessage = {
            "roomId": roomId,
            "sender": username,
            "message": messageInput.value,
            "type": 'TALK'
        };

        // 메시지를 서버로 보냄
        // /pub/chat/sendMessage: 메시지를 보낼 엔드포인트, {}: 헤더 정보를 전달하기 위한 객체, JSON: chatMessage 객체 전송
        stompClient.send("/pub/chat/sendMessage", {}, JSON.stringify(chatMessage));
        // 입력 필드 초기화
        messageInput.value = '';
    }
    // 폼 전송 이벤트 취소
    event.preventDefault();
}

// 메시지를 받을 때도 마찬가지로 JSON 타입으로 받으며,
// 넘어온 JSON 형식의 메시지를 parse 해서 사용한다.
function onMessageReceived(payload) {
    // JSON 형식의 채팅 메시지를 Javascript 객체로 변환
    var chat = JSON.parse(payload.body);

    var messageElement = document.createElement('li');

    /*
       - 채팅 메시지의 type 값을 확인하여 'enter', 'leave', 'talk'에 따라 다르게 처리
       - 'enter'와 'leave'일 경우, 'getUserList()'함수를 호출하여 사용자 목록 갱신
       - 'talk'일 경우 아바타 색상을 결정하여 avatarElement의 배경 색상을 변경하고, 'usernameElement'에 사용자 이름 추가
    */
    if (chat.type === 'ENTER') {  // chatType 이 enter 라면 아래 내용
        messageElement.classList.add('event-message');
        chat.content = chat.sender + chat.message;
        getUserList();

    } else if (chat.type === 'LEAVE') { // chatType 가 leave 라면 아래 내용
        messageElement.classList.add('event-message');
        chat.content = chat.sender + chat.message;
        getUserList();

    } else { // chatType 이 talk 라면 아래 내용용
        messageElement.classList.add('chat-message');

        var avatarElement = document.createElement('i');
        var avatarText = document.createTextNode(chat.sender[0]);
        avatarElement.appendChild(avatarText);
        avatarElement.style['background-color'] = getAvatarColor(chat.sender);

        messageElement.appendChild(avatarElement);

        var usernameElement = document.createElement('span');
        var usernameText = document.createTextNode(chat.sender);
        usernameElement.appendChild(usernameText);
        messageElement.appendChild(usernameElement);
    }

    // textEelement에 메시제 내용 추가
    var textElement = document.createElement('p');
    var messageText = document.createTextNode(chat.message);
    textElement.appendChild(messageText);

    messageElement.appendChild(textElement);

    messageArea.appendChild(messageElement);
    messageArea.scrollTop = messageArea.scrollHeight;
}


function getAvatarColor(messageSender) {
    var hash = 0;
    for (var i = 0; i < messageSender.length; i++) {
        hash = 31 * hash + messageSender.charCodeAt(i);
    }

    var index = Math.abs(hash % colors.length);
    return colors[index];
}

// 사용자 이름 입력 폼에서 submit 이벤트가 발생했을 때 connect 함수 호출
usernameForm.addEventListener('submit', connect, true)

// 메시지 입력 폼에서 submit 이벤트가 발생했을 때, sendMessage 함수 호출
messageForm.addEventListener('submit', sendMessage, true)