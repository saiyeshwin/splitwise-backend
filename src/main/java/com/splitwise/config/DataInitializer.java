package com.splitwise.config;

import com.splitwise.entity.*;
import com.splitwise.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpensesRepository expensesRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final SettlementRepository settlementRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Clear all existing data to ensure a clean, rich test database
        settlementRepository.deleteAll();
        expenseSplitRepository.deleteAll();
        expensesRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create Users
        User alice = User.builder()
                .name("Alice Smith")
                .email("alice@example.com")
                .password(passwordEncoder.encode("password"))
                .build();
        User bob = User.builder()
                .name("Bob Jones")
                .email("bob@example.com")
                .password(passwordEncoder.encode("password"))
                .build();
        User charlie = User.builder()
                .name("Charlie Brown")
                .email("charlie@example.com")
                .password(passwordEncoder.encode("password"))
                .build();
        User diana = User.builder()
                .name("Diana Prince")
                .email("diana@example.com")
                .password(passwordEncoder.encode("password"))
                .build();

        userRepository.saveAll(Arrays.asList(alice, bob, charlie, diana));

        // 2. Create Groups
        Group europeTrip = new Group();
        europeTrip.setName("Europe Trip");
        europeTrip.setCreatedBy(alice);
        groupRepository.save(europeTrip);

        Group roommates = new Group();
        roommates.setName("Roommates");
        roommates.setCreatedBy(bob);
        groupRepository.save(roommates);

        // 3. Add Members to Europe Trip (Alice, Bob, Charlie)
        GroupMember m1 = new GroupMember(null, europeTrip, alice);
        GroupMember m2 = new GroupMember(null, europeTrip, bob);
        GroupMember m3 = new GroupMember(null, europeTrip, charlie);
        groupMemberRepository.saveAll(Arrays.asList(m1, m2, m3));

        // Add Members to Roommates (Bob, Charlie, Diana)
        GroupMember m4 = new GroupMember(null, roommates, bob);
        GroupMember m5 = new GroupMember(null, roommates, charlie);
        GroupMember m6 = new GroupMember(null, roommates, diana);
        groupMemberRepository.saveAll(Arrays.asList(m4, m5, m6));

        // 4. Create Expenses
        // Expense 1: Hostel booking (Europe Trip) - Alice paid 300, Split EQUAL among Alice, Bob, Charlie
        Expense hostel = Expense.builder()
                .description("Hostel booking")
                .amount(new BigDecimal("300.00"))
                .paidBy(alice)
                .group(europeTrip)
                .splitType(SplitType.EQUAL)
                .build();
        expensesRepository.save(hostel);

        ExpenseSplit hs1 = ExpenseSplit.builder().expense(hostel).user(alice).amount(new BigDecimal("100.00")).build();
        ExpenseSplit hs2 = ExpenseSplit.builder().expense(hostel).user(bob).amount(new BigDecimal("100.00")).build();
        ExpenseSplit hs3 = ExpenseSplit.builder().expense(hostel).user(charlie).amount(new BigDecimal("100.00")).build();
        expenseSplitRepository.saveAll(Arrays.asList(hs1, hs2, hs3));

        // Expense 2: Train tickets (Europe Trip) - Bob paid 150, Split EXACT: Alice 50, Bob 50, Charlie 50
        Expense tickets = Expense.builder()
                .description("Train tickets")
                .amount(new BigDecimal("150.00"))
                .paidBy(bob)
                .group(europeTrip)
                .splitType(SplitType.EXACT)
                .build();
        expensesRepository.save(tickets);

        ExpenseSplit ts1 = ExpenseSplit.builder().expense(tickets).user(alice).amount(new BigDecimal("50.00")).build();
        ExpenseSplit ts2 = ExpenseSplit.builder().expense(tickets).user(bob).amount(new BigDecimal("50.00")).build();
        ExpenseSplit ts3 = ExpenseSplit.builder().expense(tickets).user(charlie).amount(new BigDecimal("50.00")).build();
        expenseSplitRepository.saveAll(Arrays.asList(ts1, ts2, ts3));

        // Expense 3: Dinner (Europe Trip) - Charlie paid 100, Split PERCENT: Alice 50%, Bob 30%, Charlie 20%
        Expense dinner = Expense.builder()
                .description("Dinner")
                .amount(new BigDecimal("100.00"))
                .paidBy(charlie)
                .group(europeTrip)
                .splitType(SplitType.PERCENT)
                .build();
        expensesRepository.save(dinner);

        ExpenseSplit ds1 = ExpenseSplit.builder().expense(dinner).user(alice).amount(new BigDecimal("50.00")).build();
        ExpenseSplit ds2 = ExpenseSplit.builder().expense(dinner).user(bob).amount(new BigDecimal("30.00")).build();
        ExpenseSplit ds3 = ExpenseSplit.builder().expense(dinner).user(charlie).amount(new BigDecimal("20.00")).build();
        expenseSplitRepository.saveAll(Arrays.asList(ds1, ds2, ds3));

        // Expense 4: Rent (Roommates) - Bob paid 1200, Split EQUAL among Bob, Charlie, Diana (400 each)
        Expense rent = Expense.builder()
                .description("Rent")
                .amount(new BigDecimal("1200.00"))
                .paidBy(bob)
                .group(roommates)
                .splitType(SplitType.EQUAL)
                .build();
        expensesRepository.save(rent);

        ExpenseSplit rs1 = ExpenseSplit.builder().expense(rent).user(bob).amount(new BigDecimal("400.00")).build();
        ExpenseSplit rs2 = ExpenseSplit.builder().expense(rent).user(charlie).amount(new BigDecimal("400.00")).build();
        ExpenseSplit rs3 = ExpenseSplit.builder().expense(rent).user(diana).amount(new BigDecimal("400.00")).build();
        expenseSplitRepository.saveAll(Arrays.asList(rs1, rs2, rs3));

        // Expense 5: Internet (Roommates) - Diana paid 60, Split EQUAL among Bob, Charlie, Diana (20 each)
        Expense internet = Expense.builder()
                .description("Internet bill")
                .amount(new BigDecimal("60.00"))
                .paidBy(diana)
                .group(roommates)
                .splitType(SplitType.EQUAL)
                .build();
        expensesRepository.save(internet);

        ExpenseSplit is1 = ExpenseSplit.builder().expense(internet).user(bob).amount(new BigDecimal("20.00")).build();
        ExpenseSplit is2 = ExpenseSplit.builder().expense(internet).user(charlie).amount(new BigDecimal("20.00")).build();
        ExpenseSplit is3 = ExpenseSplit.builder().expense(internet).user(diana).amount(new BigDecimal("20.00")).build();
        expenseSplitRepository.saveAll(Arrays.asList(is1, is2, is3));

        // 5. Create a Settlement: Charlie paid Bob 100.00 in Roommates group
        Settlement set1 = Settlement.builder()
                .group(roommates)
                .fromUser(charlie)
                .toUser(bob)
                .amount(new BigDecimal("100.00"))
                .build();
        settlementRepository.save(set1);
    }
}
