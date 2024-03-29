package com.gserver.domain.participate.Service;

import com.gserver.domain.participate.Model.Player;
import com.gserver.global.error.CustomException;
import com.gserver.global.error.ErrorCode;


import com.gserver.domain.game.Dto.RequestDto.RoundDto;
import com.gserver.domain.participate.Dto.ResponseDto.ItResponseDto;
import com.gserver.domain.question.Repository.CustomQuestionRepo;
import com.gserver.domain.question.Repository.DefaultQuestionRepo;
import com.gserver.domain.game.Repository.PlayerAnswerRepo;
import com.gserver.domain.participate.Dto.ResponseDto.HostResponseDto;
import com.gserver.domain.participate.Dto.ResponseDto.ParticipationResponseDto;
import com.gserver.domain.participate.Mapper.ParticipateMapper;
import com.gserver.domain.participate.Repository.PlayerRepo;
import com.gserver.domain.room.Model.Room;
import com.gserver.domain.room.Repository.RoomRepo;
import com.gserver.domain.websocket.dto.ChatMessage;
import com.gserver.global.websocket.WebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class PlayerService {


    private final EntityManager entityManager;

    @Autowired
    private RoomRepo roomRepo;
    @Autowired
    private PlayerRepo playerRepo;
    @Autowired
    private PlayerAnswerRepo playerAnswerRepo;
    @Autowired
    private DefaultQuestionRepo defaultQuestionRepo;
    @Autowired
    private CustomQuestionRepo customQuestionRepo;
    @Autowired
    private ParticipateMapper participateMapper ;
    @Autowired
    private WebSocketHandler webSocketHandler;



    public PlayerService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }



    public HostResponseDto GetHost(String roomId) {

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        // 방장 참가자 조회
        Optional<Player> hostParticipationOptional = playerRepo.findByRoomAndRoomOwnerIsTrue(room);

        // 방장이 존재하지 않으면 예외 발생
        Player hostPlayer = hostParticipationOptional
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_HOST));

        // Participation을 RoomResponseDto로 매핑하여 반환
        return participateMapper.toHostResponse(hostPlayer);
    }


    public List<ParticipationResponseDto> getParticipation(String roomId) {

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        return participateMapper.toParticipationResponse(room.getPlayers());
    }

    public ItResponseDto GameStart(RoundDto roundDTO) {
        String roomId = roundDTO.getRoomId();
        int gameRepeatCount = roundDTO.getRound();

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        //게임 시작 여부 확인
        if(room.getGameRepeat()!=0)
            throw new CustomException(ErrorCode.ALREADY_GAME_START);

        // 방에 속한 플레이어 목록 가져오기
        List<Player> players = playerRepo.findByRoom(room);
        if (players.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_ROOM);
        }

        // 플레이어 목록을 섞어 랜덤으로 술래 선택
        Collections.shuffle(players);
        Player selectedItPlayer = players.get(0); // 섞인 목록에서 첫 번째 플레이어를 술래로 선택

        // 술래로 선택된 플레이어의 it 값을 true로 변경
        selectedItPlayer.setIt(true);

        //방의 전체 round수 세팅과 현재 라운드 수 +1
        room.setGameRepeat(gameRepeatCount);
        room.setCurrentRound(room.getCurrentRound()+1);
        roomRepo.save(room);

        //웹소켓 전송
        ChatMessage message = ChatMessage.builder()
                .type(ChatMessage.MessageType.GAME_START)
                .roomNumber(roomId)
                .playerId(selectedItPlayer.getPlayerId())
                .currentRound(room.getCurrentRound())
                .build();
        try {
            webSocketHandler.handleRoomEvent(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        return participateMapper.toItResponseDto(selectedItPlayer);
    }

    @Transactional
    public void ExitPlayer(Long playerId){



        // 사용자 존재 체크 및 삭제
        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_PARTICIPATION));

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findById(player.getRoom().getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        //게임시작 유무 확인
        if(room.getGameRepeat()!=0)
            throw new CustomException(ErrorCode.NOT_EXIT_ROOM);


        // 방에 방장이 나갔다면 새로운 방장을 설정
        if (player.isRoomOwner()) {
            List<Player> remainingParticipants = room.getPlayers();
            if (remainingParticipants.size() > 1) {
                Player newRoomOwner = remainingParticipants.get(1);
                newRoomOwner.setRoomOwner(true);
                playerRepo.save(newRoomOwner);
            }
        }

        // 방 참가자 수 갱신
        room.setPlayerCount(room.getPlayerCount() - 1);
        roomRepo.save(room);

        // 참가자 삭제
        playerRepo.delete(player);

        // 방에 참가자가 1명일 때 방 삭제
        if (room.getPlayerCount() == 0)
            roomRepo.delete(room);

    }




}
