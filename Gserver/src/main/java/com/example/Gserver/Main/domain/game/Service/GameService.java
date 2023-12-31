package com.example.Gserver.Main.domain.game.Service;

import com.example.Gserver.Main.domain.question.Dto.Request.CustomQuestionDto;
import com.example.Gserver.error.CustomException;
import com.example.Gserver.error.ErrorCode;
import com.example.Gserver.Main.domain.game.Dto.RequestDto.*;
import com.example.Gserver.Main.domain.game.Dto.ResponseDto.AnswersResponseDto;
import com.example.Gserver.Main.domain.game.Dto.ResponseDto.CorrectResultResponseDto;
import com.example.Gserver.Main.domain.game.Dto.ResponseDto.ItResponseDto;
import com.example.Gserver.Main.domain.game.Dto.ResponseDto.QuestionResponseDto;
import com.example.Gserver.Main.domain.game.Mapper.GameMapper;
import com.example.Gserver.Main.domain.question.Model.CustomQuestion;
import com.example.Gserver.Main.domain.question.Model.DefaultQuestion;
import com.example.Gserver.Main.domain.game.Model.PlayerAnswer;
import com.example.Gserver.Main.domain.question.Repository.CustomQuestionRepo;
import com.example.Gserver.Main.domain.question.Repository.DefaultQuestionRepo;
import com.example.Gserver.Main.domain.game.Repository.PlayerAnswerRepo;
import com.example.Gserver.Main.domain.participate.Model.Player;
import com.example.Gserver.Main.domain.participate.Repository.PlayerRepo;
import com.example.Gserver.Main.domain.room.Model.Room;
import com.example.Gserver.Main.domain.room.Repository.RoomRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class GameService {


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
    private GameMapper gameMapper;



    public GameService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }


    public void SaveDefaultQuestion(String question){
        DefaultQuestion defaultQuestion = new DefaultQuestion(question);
        defaultQuestionRepo.save(defaultQuestion);
    }

    public QuestionResponseDto GetQuestion(String roomId){

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        // 전체 질문 수 조회
        int questionCount = defaultQuestionRepo.countBy();

        // 질문이 하나도 없으면 예외 발생
        if (questionCount == 0) {
            throw new CustomException(ErrorCode.NOT_EXIST_QUESTION);
        }

        // 랜덤하게 질문 선택
        Random random = new Random();
        int randomNumber = random.nextInt((int) questionCount)+1;

        // 선택된 질문 조회
        DefaultQuestion defaultQuestion = defaultQuestionRepo.findByDefaultQuestionId(randomNumber);

        // QuestionResponseDto 생성 및 반환
        return gameMapper.toQuestionResponse(room, defaultQuestion);
    }


    public void CompleteAnswer(ParticipationAnswerDto participationAnswerDTO) {
        String roomId = participationAnswerDTO.getRoomId();
        Long playerId = participationAnswerDTO.getPlayerId();
        String answer = participationAnswerDTO.getAnswer();

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        // 참가자 조회 (없으면 예외 발생)
        Player PLAYER = playerRepo.findById(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_PARTICIPATION));

        // 참가작 질문 답변 생성 및 저장
        PlayerAnswer playerAnswer = gameMapper.toPlayerAnswer(PLAYER, participationAnswerDTO);
        playerAnswerRepo.save(playerAnswer);
    }


    public List<AnswersResponseDto> GetAnswers(String roomId, int currentCount) {

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        // 방에 속한 플레이어 목록 가져오기
        List<Player> players = playerRepo.findByRoom(room);

        // 플레이어 응답 조회 및 DTO로 변환
        List<AnswersResponseDto> answers = players.stream()
                .map(player -> playerAnswerRepo.findByPlayerAndAnswerRound(player, currentCount))
                .filter(Objects::nonNull)
                .map(playerAnswer -> new AnswersResponseDto(playerAnswer.getPlayer().getNickName(), playerAnswer.getAnswer()))
                .collect(Collectors.toList());

        return answers;
    }






    @Transactional
    public CorrectResultResponseDto CompareAnswer(ParticipationAnswerListDto participationAnswerListDTO){
        String roomId = participationAnswerListDTO.getRoomId();
        Long playerId = participationAnswerListDTO.getPlayerId();
        int answerRound = participationAnswerListDTO.getAnswerRound();
        Long playerIdList[] = participationAnswerListDTO.getPlayerIdList();
        String answerList[] = participationAnswerListDTO.getAnswerList();

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        // 참가자 조회 (없으면 예외 발생)
        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_PARTICIPATION));

        int correctCount = 0;

        // playerIdList에 있는 사용자들의 답변을 찾아서 비교
        for (int i = 0; i < playerIdList.length; i++) {
            Long currentPlayerId = playerIdList[i];

            // playerIdList에 있는 각 사용자의 답변 조회
            Player currentPlayer = playerRepo.findById(currentPlayerId)
                    .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_PARTICIPATION));

            // 현재 사용자의 특정 라운드의 답변 조회
            PlayerAnswer currentAnswer = playerAnswerRepo.findByPlayerAndAnswerRound(currentPlayer, answerRound);
            if (currentAnswer != null) {
                // 정답 비교
                String expectedAnswer = answerList[i];
                String actualAnswer = currentAnswer.getAnswer();
                if (expectedAnswer.equals(actualAnswer)) {
                    correctCount++;
                }
            }
        }

        //정답개수 업데이트
        player.setCorrectAnswer(correctCount);
        playerRepo.save(player);

        return gameMapper.toCorrectResultResponse(player.getCorrectAnswer());
    }

    @Transactional
    public ItResponseDto changeIt(RoomDto roomDTO) {
        String roomId = roomDTO.getRoomId();

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        // 방에 속한 플레이어 목록 가져오기
        List<Player> players = playerRepo.findByRoom(room);
        if (players.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_ROOM);
        }

        // 현재 술래 찾기
        Player currentItPlayer = players.stream()
                .filter(Player::isIt)
                .findFirst()
                .orElse(null);

        // 다음 술래 찾기
        Player nextItPlayer = null;
        if (currentItPlayer != null) {
            int nextIndex = (players.indexOf(currentItPlayer) + 1) % players.size();
            nextItPlayer = players.get(nextIndex);
        } else {
            nextItPlayer = players.get(0);
        }

        // 현재 술래의 it 값을 false로 변경
        if (currentItPlayer != null) {
            currentItPlayer.setIt(false);
            playerRepo.save(currentItPlayer);
        }

        // 다음 술래의 it 값을 true로 변경
        nextItPlayer.setIt(true);
        playerRepo.save(nextItPlayer);

        //현재 게임 라운드 수 +1
        room.setCurrentRound(room.getCurrentRound()+1);
        roomRepo.save(room);

        // 변경된 플레이어 정보를 DTO로 반환
        return gameMapper.toItResponseDto(nextItPlayer);
    }


    public CorrectResultResponseDto CorrectResult(Long playerId){


        // 플레이어 조회 (없으면 예외 발생)
        Player player = playerRepo.findById(Long.valueOf(playerId))
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_PARTICIPATION));

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(player.getRoom().getRoomId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        return gameMapper.toCorrectResultResponse(player.getCorrectAnswer());
    }



    public void FinishGame(String roomId){

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        // 게임 종료 후, 방 삭제
        roomRepo.delete(room);
    }

    public void SaveCustomQuestion(CustomQuestionDto customQuestionDto){
        String roomId = customQuestionDto.getRoomId();
        String question = customQuestionDto.getQuestion();

        // 방 조회 (없으면 예외 발생)
        Room room = roomRepo.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_ROOM));

        //엔티티 변환 후 저장
        CustomQuestion customQuestion = gameMapper.toCustomQuestion(room, customQuestionDto);
        customQuestionRepo.save(customQuestion);
    }



}
