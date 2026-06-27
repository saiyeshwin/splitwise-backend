package com.splitwise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.dto.*;
import com.splitwise.entity.SplitType;
import com.splitwise.entity.User;
import com.splitwise.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class EndToEndIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ExpensesRepository expensesRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    public void testFullFlowWithSecurityAndSplits() throws Exception {
        settlementRepository.deleteAll();
        expenseSplitRepository.deleteAll();
        expensesRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Register users: John, Sai, Alice
        RegisterRequestDTO johnReg = new RegisterRequestDTO();
        johnReg.setName("John");
        johnReg.setEmail("john@example.com");
        johnReg.setPassword("password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(johnReg)))
                .andExpect(status().isOk());

        RegisterRequestDTO saiReg = new RegisterRequestDTO();
        saiReg.setName("Sai");
        saiReg.setEmail("sai@example.com");
        saiReg.setPassword("password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saiReg)))
                .andExpect(status().isOk());

        RegisterRequestDTO aliceReg = new RegisterRequestDTO();
        aliceReg.setName("Alice");
        aliceReg.setEmail("alice@example.com");
        aliceReg.setPassword("password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aliceReg)))
                .andExpect(status().isOk());

        // 2. Login to get JWT Token
        LoginRequestDTO loginReq = new LoginRequestDTO();
        loginReq.setEmail("john@example.com");
        loginReq.setPassword("password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andReturn();

        AuthResponseDTO authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponseDTO.class
        );
        String jwtToken = "Bearer " + authResponse.getToken();

        User john = userRepository.findByEmail("john@example.com").orElseThrow();
        User sai = userRepository.findByEmail("sai@example.com").orElseThrow();
        User alice = userRepository.findByEmail("alice@example.com").orElseThrow();

        // 3. Test Security Bypass Block: Request without token should fail (403 Forbidden)
        mockMvc.perform(get("/groups"))
                .andExpect(status().isForbidden());

        // 4. Create Group (Secured - passing token)
        CreateGroupRequestDTO createGroup = new CreateGroupRequestDTO();
        createGroup.setName("Trip");
        createGroup.setCreatedById(john.getId());

        MvcResult groupResult = mockMvc.perform(post("/groups")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createGroup)))
                .andExpect(status().isOk())
                .andReturn();

        GroupResponseDTO group = objectMapper.readValue(
                groupResult.getResponse().getContentAsString(),
                GroupResponseDTO.class
        );

        // 5. Add Members (Secured)
        AddMemberRequestDTO addJohn = new AddMemberRequestDTO();
        addJohn.setUserId(john.getId());
        mockMvc.perform(post("/groups/" + group.getId() + "/members")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addJohn)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk());

        AddMemberRequestDTO addSai = new AddMemberRequestDTO();
        addSai.setUserId(sai.getId());
        mockMvc.perform(post("/groups/" + group.getId() + "/members")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addSai)))
                .andExpect(status().isOk());

        AddMemberRequestDTO addAlice = new AddMemberRequestDTO();
        addAlice.setUserId(alice.getId());
        mockMvc.perform(post("/groups/" + group.getId() + "/members")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addAlice)))
                .andExpect(status().isOk());

        // 6. Test Group Membership Validation (Payer not in group)
        User externalUser = User.builder().name("External").email("ext@example.com").password("pass").build();
        userRepository.save(externalUser);

        ExpenseRequestDTO invalidExpenseReq = ExpenseRequestDTO.builder()
                .description("Invalid Expense")
                .amount(BigDecimal.valueOf(100.00))
                .paidByUserId(externalUser.getId())
                .groupId(group.getId())
                .splitType(SplitType.EQUAL)
                .build();

        mockMvc.perform(post("/expenses")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidExpenseReq)))
                .andExpect(status().isBadRequest());

        // 7. Add Expense: EQUAL Split (Dinner - 300)
        ExpenseRequestDTO equalExpense = ExpenseRequestDTO.builder()
                .description("Dinner")
                .amount(BigDecimal.valueOf(300.00))
                .paidByUserId(john.getId())
                .groupId(group.getId())
                .splitType(SplitType.EQUAL)
                .build();

        mockMvc.perform(post("/expenses")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(equalExpense)))
                .andExpect(status().isOk());

        // Check balances: Sai owes John 100, Alice owes John 100
        MvcResult balancesResult1 = mockMvc.perform(get("/groups/" + group.getId() + "/balances")
                .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        List<BalanceResponseDTO> balances1 = Arrays.asList(objectMapper.readValue(
                balancesResult1.getResponse().getContentAsString(),
                BalanceResponseDTO[].class
        ));
        assertEquals(2, balances1.size());

        // 8. Add Expense: EXACT Split (Cab - 150)
        // John pays 150. Splits: Sai owes 90, Alice owes 60. (Total 150)
        ExpenseRequestDTO exactExpense = ExpenseRequestDTO.builder()
                .description("Cab")
                .amount(BigDecimal.valueOf(150.00))
                .paidByUserId(john.getId())
                .groupId(group.getId())
                .splitType(SplitType.EXACT)
                .splits(Arrays.asList(
                        new SplitRequestDTO(sai.getId(), BigDecimal.valueOf(90.00)),
                        new SplitRequestDTO(alice.getId(), BigDecimal.valueOf(60.00))
                ))
                .build();

        mockMvc.perform(post("/expenses")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exactExpense)))
                .andExpect(status().isOk());

        // 9. Add Expense: PERCENT Split (Snacks - 100)
        // John pays 100. Splits: John 20%, Sai 50%, Alice 30%. (Total 100%)
        ExpenseRequestDTO percentExpense = ExpenseRequestDTO.builder()
                .description("Snacks")
                .amount(BigDecimal.valueOf(100.00))
                .paidByUserId(john.getId())
                .groupId(group.getId())
                .splitType(SplitType.PERCENT)
                .splits(Arrays.asList(
                        new SplitRequestDTO(john.getId(), BigDecimal.valueOf(20.00)),
                        new SplitRequestDTO(sai.getId(), BigDecimal.valueOf(50.00)),
                        new SplitRequestDTO(alice.getId(), BigDecimal.valueOf(30.00))
                ))
                .build();

        mockMvc.perform(post("/expenses")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(percentExpense)))
                .andExpect(status().isOk());

        // Let's check updated balances
        // Dinner (EQUAL): John (+200), Sai (-100), Alice (-100)
        // Cab (EXACT): John (+150), Sai (-90), Alice (-60)
        // Snacks (PERCENT): John (+80), Sai (-50), Alice (-30)
        // Net Balances:
        // John: 200 + 150 + 80 = +430
        // Sai: -100 - 90 - 50 = -240
        // Alice: -100 - 60 - 30 = -190
        MvcResult balancesResult2 = mockMvc.perform(get("/groups/" + group.getId() + "/balances")
                .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        List<BalanceResponseDTO> balances2 = Arrays.asList(objectMapper.readValue(
                balancesResult2.getResponse().getContentAsString(),
                BalanceResponseDTO[].class
        ));
        assertEquals(2, balances2.size());

        BalanceResponseDTO saiBalance = balances2.stream()
                .filter(b -> b.getDebtorName().equals("Sai"))
                .findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(240.00).compareTo(saiBalance.getAmount()));

        BalanceResponseDTO aliceBalance = balances2.stream()
                .filter(b -> b.getDebtorName().equals("Alice"))
                .findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(190.00).compareTo(aliceBalance.getAmount()));

        // 10. Settle Expense: Sai pays John 240
        SettlementRequestDTO settleSai = SettlementRequestDTO.builder()
                .groupId(group.getId())
                .fromUserId(sai.getId())
                .toUserId(john.getId())
                .amount(BigDecimal.valueOf(240.00))
                .build();

        mockMvc.perform(post("/settlements")
                .header("Authorization", jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(settleSai)))
                .andExpect(status().isOk());

        // Check balances again. Sai should be settled. Only Alice owes John 190.
        MvcResult balancesResult3 = mockMvc.perform(get("/groups/" + group.getId() + "/balances")
                .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        List<BalanceResponseDTO> balances3 = Arrays.asList(objectMapper.readValue(
                balancesResult3.getResponse().getContentAsString(),
                BalanceResponseDTO[].class
        ));
        assertEquals(1, balances3.size());
        assertEquals("Alice", balances3.get(0).getDebtorName());
        assertEquals(0, BigDecimal.valueOf(190.00).compareTo(balances3.get(0).getAmount()));
    }
}
