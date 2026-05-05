package com.semi.domain.member;

import com.semi.domain.member.dto.MemberResponse;
import com.semi.domain.member.dto.RegisterRequest;
import com.semi.domain.member.dto.UpdateProfileRequest;
import com.semi.domain.order.PurchaseOrder;
import com.semi.domain.order.PurchaseOrderRepository;
import com.semi.exception.DuplicateMemberException;
import com.semi.exception.MemberNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PurchaseOrderRepository purchaseOrderRepository;

  

    /**
     * 회원가입
     * - 아이디/이메일 중복 체크 후 BCrypt 해시 저장
     */
    @Transactional
    public MemberResponse register(RegisterRequest request) {
        // 아이디 중복 확인
        if (memberRepository.existsByMemberId(request.memberId())) {
            throw new DuplicateMemberException("이미 사용 중인 아이디입니다.");
        }
        // 이메일 중복 확인
        if (memberRepository.existsByEmail(request.email())) {
            throw new DuplicateMemberException("이미 사용 중인 이메일입니다.");
        }

        Member member = Member.builder()
                .memberId(request.memberId())
                .password(passwordEncoder.encode(request.password()))  // BCrypt 해시
                .email(request.email())
                .phone(request.phone())
                .name(request.name())
                .build();

        return MemberResponse.from(memberRepository.save(member));
    }

    /**
     * 관리자가 특정 회원의 권한을 변경
     * @param memberId  대상 회원 PK
     * @param newRole   변경할 권한
     * @param changedBy 변경한 관리자 아이디 (audit 로그용)
     */
    @Transactional
    public MemberResponse updateRole(Long memberId, MemberRole newRole, String changedBy) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("회원을 찾을 수 없습니다."));

        MemberRole oldRole = member.getRole();
        member.updateRole(newRole);

        // 권한 변경 audit 로그 — 누가 언제 어떤 회원의 권한을 바꿨는지 추적
        log.info("[AUDIT] role_change | target={} | {} -> {} | changedBy={}",
                member.getMemberId(), oldRole, newRole, changedBy);

        return MemberResponse.from(member);
    }

    /**
     * 관리자용 회원 목록 조회.
     */
    // public List<MemberResponse> getAllMembers() {
    //     return memberRepository.findAll().stream()
    //             .map(MemberResponse::from)
    //             .collect(Collectors.toList());
    // }
  @Transactional(readOnly = true)
    public List<MemberResponse> getAllMembers() {
        return memberRepository.findAll().stream()
                .map(MemberResponse::from)
                .toList();
    }
    /**
     * 회원 삭제. 연결된 주문도 함께 정리한다.
     */
    @Transactional
    public void deleteMember(Long memberId, String deletedBy) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("회원을 찾을 수 없습니다."));

        if (member.getRole() == MemberRole.ADMIN) {
            throw new IllegalStateException("관리자 계정은 삭제할 수 없습니다.");
        }

        List<PurchaseOrder> orders = purchaseOrderRepository.findByMemberIdOrderByOrderedAtDesc(memberId);
        purchaseOrderRepository.deleteAll(orders);

        memberRepository.delete(member);

        log.info("[AUDIT] member_deleted | target={} | deletedBy={}", member.getMemberId(), deletedBy);
    }
    
    @Transactional
    public MemberResponse updateProfile(Long memberId, UpdateProfileRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("회원을 찾을 수 없습니다."));

        if (memberRepository.existsByEmailAndIdNot(request.email(), memberId)) {
            throw new DuplicateMemberException("이미 사용 중인 이메일입니다.");
        }

        member.updateProfile(request.email(), request.phone());
        return MemberResponse.from(member);
    }

    @Transactional
    public Optional<Member> findByMemberId(String memberId){
        return memberRepository.findByMemberId(memberId);
    }
}
