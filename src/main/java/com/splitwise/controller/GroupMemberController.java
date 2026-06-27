import com.splitwise.dto.AddMemberRequestDTO;
import com.splitwise.dto.GroupMemberResponseDTO;
import com.splitwise.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/groups")
public class GroupMemberController {
    private final GroupMemberService groupMemberService;

    @PostMapping("/{groupId}/members")
    public GroupMemberResponseDTO addMember(@PathVariable Long groupId,
                                            @RequestBody AddMemberRequestDTO addMemberRequestDTO){
        return groupMemberService.addMember(groupId, addMemberRequestDTO);
    }

    @GetMapping("/{groupId}/members")
    public List<GroupMemberResponseDTO> getMembers(@PathVariable Long groupId){
        return groupMemberService.getMembers(groupId);
    }
}