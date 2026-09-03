/** 폼 전체에 걸리는 서버 오류 한 줄. 필드별 오류는 TextField 가 맡는다. */
export function FormAlert({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p
      role="alert"
      className="rounded-xl bg-red-50 px-4 py-3 text-[13px] text-red-600"
    >
      {message}
    </p>
  );
}
