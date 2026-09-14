package com.gommit.domain.checkin.event;

// 인증 저장 후 후처리(포인트 적립·스트릭 갱신 등)가 실패해 트랜잭션이 롤백될 때, 이미 스토리지에
// 올라간 미디어를 정리하기 위해 발행된다. 리스너가 롤백 이후(after-rollback)에 삭제를 수행해
// 외부 스토리지 호출이 DB 커넥션을 붙잡지 않도록 한다.
public record OrphanMediaCleanupEvent(String mediaKey, String posterKey) {}
