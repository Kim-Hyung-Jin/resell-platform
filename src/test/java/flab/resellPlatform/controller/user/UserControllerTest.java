package flab.resellPlatform.controller.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import flab.resellPlatform.MessageConfig;
import flab.resellPlatform.common.utils.UserUtils;
import flab.resellPlatform.controller.response.StandardResponse;
import flab.resellPlatform.data.UserTestFactory;
import flab.resellPlatform.domain.user.LoginInfo;
import flab.resellPlatform.domain.user.StrictLoginInfo;
import flab.resellPlatform.domain.user.UserDTO;
import flab.resellPlatform.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserController.class, MessageConfig.class})
@WithMockUser
class UserControllerTest {

    @Autowired
    MessageSourceAccessor messageSourceAccessor;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RandomValueStringGenerator randomValueStringGenerator;

    @MockBean
    PasswordEncoder passwordEncoder;

    @MockBean
    UserService userService;

    ObjectMapper mapper = new ObjectMapper();

    @DisplayName("아이디 생성 성공")
    @Test
    void createUser_success() throws Exception {
        // given
        UserDTO userDTO = UserTestFactory.createUserDTOBuilder().build();
        when(userService.createUser(any())).thenReturn(Optional.of(userDTO));
        String userData = mapper.writeValueAsString(userDTO);

        // when
        ResultActions resultActions = mockMvc.perform(post("/users/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userData)
                .with(csrf()));
        // then
        resultActions.andExpect(status().isOk());
    }

    @DisplayName("아이디 생성 실패 by 유효성 검사")
    @Test
    void createUser_failByValidation() throws Exception {
        // given
        UserDTO userDTO = UserTestFactory.createUserDTOBuilder()
                .email("alstjrdl852naver.com")
                .build();
        String userData = mapper.writeValueAsString(userDTO);

        // when
        ResultActions resultActions = mockMvc.perform(post("/users/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userData)
                .with(csrf()));
        // then
        Map<String, Object> returnObjects = Map.of();
        StandardResponse defaultResponse = StandardResponse.builder()
                .message(messageSourceAccessor.getMessage("common.argument.invalid"))
                .data(returnObjects)
                .build();

        expectDefaultResponse(defaultResponse, status().isBadRequest(), resultActions);
    }

    @DisplayName("아이디 생성 실패 by 아이디 중복")
    @Test
    void createUser_failByIdDuplication() throws Exception {
        // given
        UserDTO userDTO = UserTestFactory.createUserDTOBuilder().build();
        when(userService.createUser(any())).thenAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                throw new SQLIntegrityConstraintViolationException();
            }
        });
        String userData = mapper.writeValueAsString(userDTO);

        // when
        ResultActions resultActions = mockMvc.perform(post("/users/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userData)
                .with(csrf()));

        // then
        Map<String, Object> returnObjects = Map.of();
        StandardResponse defaultResponse = StandardResponse.builder()
                .message(messageSourceAccessor.getMessage("user.username.duplicated"))
                .data(returnObjects)
                .build();
        expectDefaultResponse(defaultResponse, status().isBadRequest(), resultActions);
    }

    @DisplayName("아이디 찾기 성공")
    @Test
    void findUsername_found() throws Exception {
        // given
        String targetUsername = "michael";
        String requiredPhoneNumber = "010-4589-0000";
        String normalizedPhoneNumber = UserUtils.normalizePhoneNumber(requiredPhoneNumber);
        String query = "phoneNumber=" + requiredPhoneNumber;
        when(userService.findUsername(normalizedPhoneNumber)).thenReturn(Optional.of(targetUsername));

        // when
        ResultActions resultActions = mockMvc.perform(get("/users/usernameInquiry" + "?" + query)
                .with(csrf()));

        // then
        StandardResponse standardResponse = StandardResponse.builder()
                .message(messageSourceAccessor.getMessage("user.username.found"))
                .data(Map.of("username", targetUsername))
                .build();

        expectDefaultResponse(standardResponse, status().isOk(), resultActions);
    }

    @DisplayName("아이디 찾기 실패")
    @Test
    void findUsername_phoneNumberNotFound() throws Exception {
        // given
        String targetUsername = "michael";
        String requiredPhoneNumber = "010-4589-0000";
        String query = "phoneNumber=" + requiredPhoneNumber;
        when(userService.findUsername(requiredPhoneNumber)).thenReturn(Optional.empty());

        // when
        ResultActions resultActions = mockMvc.perform(get("/users/usernameInquiry" + "?" + query)
                .with(csrf()));

        // then
        ObjectMapper mapper = new ObjectMapper();
        StandardResponse standardResponse = StandardResponse.builder()
                .message(messageSourceAccessor.getMessage("user.phoneNumber.notFound"))
                .data(Map.of())
                .build();

        expectDefaultResponse(standardResponse, status().isBadRequest(), resultActions);
    }

    @DisplayName("비밀번호 찾기 성공 by 임시 비밀번호 발급")
    @Test
    void findPassword_success() throws Exception {
        // given
        StrictLoginInfo strictLoginInfo = UserTestFactory.createStrictLoginInfoBuilder().build();
        String body = mapper.writeValueAsString(strictLoginInfo);
        String temporaryPassword = "fslkkjlk12";
        when(randomValueStringGenerator.generate()).thenReturn(temporaryPassword);
        when(userService.updatePassword((StrictLoginInfo) any())).thenReturn(1);

        // when
        ResultActions resultActions = mockMvc.perform(post("/users/password/inquiry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()));

        // then
        StandardResponse standardResponse = StandardResponse.builder()
                .data(Map.of("password", temporaryPassword))
                .message(messageSourceAccessor.getMessage("user.temporary.password.returned"))
                .build();
        expectDefaultResponse(standardResponse, status().isOk(), resultActions);

    }

    @DisplayName("비밀번호 찾기 실패 by 유저 정보 불일치")
    @Test
    void findPassword_failure() throws Exception {
        // given
        StrictLoginInfo strictLoginInfo = UserTestFactory.createStrictLoginInfoBuilder().build();
        String body = mapper.writeValueAsString(strictLoginInfo);
        String temporaryPassword = "fslkkjlk12";
        when(randomValueStringGenerator.generate()).thenReturn(temporaryPassword);
        when(userService.updatePassword(strictLoginInfo)).thenReturn(0);

        // when
        ResultActions resultActions = mockMvc.perform(post("/users/password/inquiry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()));

        // then
        StandardResponse standardResponse = StandardResponse.builder()
                .data(Map.of())
                .message(messageSourceAccessor.getMessage("user.userInfo.notFound"))
                .build();
        expectDefaultResponse(standardResponse, status().isBadRequest(), resultActions);

    }

    @DisplayName("패스워드 업데이트 성공")
    @Test
    void updatePassword_success() throws Exception {
        // given
        LoginInfo loginInfo = UserTestFactory.createLoginInfoBuilder().build();
        String body = mapper.writeValueAsString(loginInfo);
        when(passwordEncoder.encode(any())).thenReturn("encodedPW");
        when(userService.updatePassword((LoginInfo) any())).thenReturn(1);


        // when
        ResultActions resultActions = mockMvc.perform(post("/users/password/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()));

        // then
        StandardResponse standardResponse = StandardResponse.builder()
                .message(messageSourceAccessor.getMessage("user.password.update.succeeded"))
                .data(Map.of())
                .build();
        expectDefaultResponse(standardResponse, status().isOk(), resultActions);
    }

    @DisplayName("패스워드 업데이트 실패")
    @Test
    void updatePassword_failure() throws Exception {
        // given
        LoginInfo loginInfo = UserTestFactory.createLoginInfoBuilder().build();
        String body = mapper.writeValueAsString(loginInfo);
        when(passwordEncoder.encode(any())).thenReturn("encodedPW");
        when(userService.updatePassword((LoginInfo) any())).thenReturn(0);


        // when
        ResultActions resultActions = mockMvc.perform(post("/users/password/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()));

        // then
        StandardResponse standardResponse = StandardResponse.builder()
                .message(messageSourceAccessor.getMessage("user.userInfo.notFound"))
                .data(Map.of())
                .build();
        expectDefaultResponse(standardResponse, status().isBadRequest(), resultActions);
    }
    
    private void expectDefaultResponse(StandardResponse standardResponse, ResultMatcher status, ResultActions resultActions) throws Exception {
        String defaultResponseJson = mapper.writeValueAsString(standardResponse);
        resultActions
                .andExpect(status)
                .andExpect(content().string(defaultResponseJson));
    }
}
