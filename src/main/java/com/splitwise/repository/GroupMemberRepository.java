package com.splitwise.repository;

import com.splitwise.entity.Group;
import com.splitwise.entity.GroupMember;
import com.splitwise.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember,Long> {
    boolean existsByGroupAndUser(Group group, User user);
}
