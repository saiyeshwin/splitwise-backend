package com.splitwise;

import com.splitwise.dto.*;
import com.splitwise.entity.User;
import com.splitwise.repository.UserRepository;
import com.splitwise.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class EndToEndIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testEndToEndFlow() {
        // 1. Register users: John, Sai, Alice
        String emailJohn = "john" + System.currentTimeMillis() + "@example.com";
        String emailSai = "sai" + System.currentTimeMillis() + "@example.com";
        String emailAlice = "alice" + System.currentTimeMillis() + "@example.com";

        RegisterRequestDTO johnReg = new RegisterRequestDTO();
        johnReg.setName("John");
        johnReg.setEmail(emailJohn);
        johnReg.setPassword("password123");
        authService.register(johnReg);

        RegisterRequestDTO saiReg = new RegisterRequestDTO();
        saiReg.setName("Sai");
        saiReg.setEmail(emailSai);
        saiReg.setPassword("password123");
        authService.register(saiReg);

        RegisterRequestDTO aliceReg = new RegisterRequestDTO();
        aliceReg.setName("Alice");
        aliceReg.setEmail(emailAlice);
        aliceReg.setPassword("password123");
        authService.register(aliceReg);

        User john = userRepository.findByEmail(emailJohn).orElseThrow();
        User sai = userRepository.findByEmail(emailSai).orElseThrow();
        User alice = userRepository.findByEmail(emailAlice).orElseThrow();

        // 2. Create group "Trip" by John
        CreateGroupRequestDTO createGroup = new CreateGroupRequestDTO();
        createGroup.setName("Trip");
        createGroup.setCreatedById(john.getId());
        GroupResponseDTO group = groupService.createGroup(createGroup);

        // 3. Add members to group (John, Sai, Alice)
        AddMemberRequestDTO addJohn = new AddMemberRequestDTO();
        addJohn.setUserId(john.getId());
        groupMemberService.addMember(group.getId(), addJohn);

        AddMemberRequestDTO addSai = new AddMemberRequestDTO();
        addSai.setUserId(sai.getId());
        groupMemberService.addMember(group.getId(), addSai);

        AddMemberRequestDTO addAlice = new AddMemberRequestDTO();
        addAlice.setUserId(alice.getId());
        groupMemberService.addMember(group.getId(), addAlice);

        // 4. John pays 300 for trip expense
        CreateExpenseRequestDTO expenseReq = new CreateExpenseRequestDTO();
        expenseReq.setDescription("Dinner");
        expenseReq.setAmount(BigDecimal.valueOf(300.00));
        expenseReq.setPaidById(john.getId());
        expenseReq.setGroupId(group.getId());
        CreateExpenseResponseDTO expenseResponse = expenseService.createExpense(expenseReq);

        // 5. Get balances. Since John paid 300 and split is EQUAL:
        // John's net balance: +200
        // Sai's net balance: -100
        // Alice's net balance: -100
        // Debt resolution: Sai owes John 100, Alice owes John 100.
        List<BalanceResponseDTO> balances = groupService.getGroupBalances(group.getId());
        assertEquals(2, balances.size());

        BalanceResponseDTO b1 = balances.stream()
                .filter(b -> b.getDebtorName().equals("Sai"))
                .findFirst().orElseThrow();
        assertEquals("John", b1.getCreditorName());
        assertEquals(0, BigDecimal.valueOf(100.00).compareTo(b1.getAmount()));

        BalanceResponseDTO b2 = balances.stream()
                .filter(b -> b.getDebtorName().equals("Alice"))
                .findFirst().orElseThrow();
        assertEquals("John", b2.getCreditorName());
        assertEquals(0, BigDecimal.valueOf(100.00).compareTo(b2.getAmount()));

        // 6. Sai settles 100 to John
        SettlementRequestDTO settlementReq = SettlementRequestDTO.builder()
                .groupId(group.getId())
                .fromUserId(sai.getId())
                .toUserId(john.getId())
                .amount(BigDecimal.valueOf(100.00))
                .build();
        settlementService.settleExpense(settlementReq);

        // 7. Get balances again.
        // Sai's debt should be resolved. Only Alice owes John 100.
        List<BalanceResponseDTO> balancesAfterSettlement = groupService.getGroupBalances(group.getId());
        assertEquals(1, balancesAfterSettlement.size());

        BalanceResponseDTO remainingBalance = balancesAfterSettlement.get(0);
        assertEquals("Alice", remainingBalance.getDebtorName());
        assertEquals("John", remainingBalance.getCreditorName());
        assertEquals(0, BigDecimal.valueOf(100.00).compareTo(remainingBalance.getAmount()));
    }
}
