/** 탭 화면 상단의 제목 줄. */
export function PageHeader({ title }: { title: string }) {
  return (
    <header className="px-6 pt-5 pb-1">
      <h1 className="text-[26px] font-bold text-gray-900">{title}</h1>
    </header>
  );
}
