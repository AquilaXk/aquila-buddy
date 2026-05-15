package com.aquilabuddy.application.service.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.domain.analysis.IssueGuide;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IssueGuideService implements GetIssueGuideUseCase {

    @Override
    public IssueGuide findByIssueId(String issueId) {
        if ("demo-2".equals(issueId)) {
            return new IssueGuide(
                issueId,
                "입력 검증 흐름을 따라가며 실패 응답 메시지와 예시 코드를 함께 정리합니다.",
                String.join("\n",
                    "1. 검증 대상 입력과 실패 조건을 확인한다.",
                    "2. 현재 실패 응답 형식을 테스트로 고정한다.",
                    "3. 예시 코드와 설명을 같은 용어로 맞춘다.",
                    "4. 정상 입력과 실패 입력을 모두 재검증한다."
                ),
                List.of(
                    "검증 메시지 문구가 바뀌면 관련 문서와 테스트 기대값도 함께 바뀔 수 있습니다.",
                    "동일 응답 형식을 사용하는 화면의 에러 표시 문구를 확인해야 합니다."
                )
            );
        }

        return new IssueGuide(
            issueId,
            "변경 범위를 작게 유지하면서 현재 문서 흐름과 실제 첫 기여 절차가 어긋나는 지점을 보완합니다.",
            String.join("\n",
                "1. 상세 화면에서 제공하는 이슈 요약과 라벨을 확인한다.",
                "2. 누락된 안내 문장을 테스트 가능한 단위로 나눈다.",
                "3. 한 번에 하나의 문단 또는 예시만 수정한다.",
                "4. 변경 후 화면과 테스트에서 동일한 용어가 쓰이는지 확인한다."
            ),
            List.of(
                "문구 변경은 화면 스냅샷이나 문서 테스트 기대값에 영향을 줄 수 있습니다.",
                "기존 안내와 중복되는 설명이 생기지 않도록 주변 문맥을 확인해야 합니다."
            )
        );
    }
}
