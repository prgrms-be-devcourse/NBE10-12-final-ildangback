import { CaretRightIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { Checkbox } from "../../../shared/ui/Checkbox";
import { TermsModal } from "../../../shared/ui/TermsModal";
import { TERMS_DOCUMENTS, type TermsState } from "./terms";

const ITEMS = [
  { key: "service", required: true, title: "이용약관 동의" },
  { key: "privacy", required: true, title: "개인정보 처리방침 동의" },
  { key: "marketing", required: false, title: "이벤트 · 혜택 알림 동의" },
] as const;

interface Props {
  value: TermsState;
  onChange(next: TermsState): void;
}

export function TermsAgreement({ value, onChange }: Props) {
  const allChecked = value.service && value.privacy && value.marketing;
  const [modalDoc, setModalDoc] = useState<{
    title: string;
    content: string;
  } | null>(null);

  return (
    <>
      <fieldset>
        <legend className="mb-2 text-[13px] font-semibold text-gray-900">
          약관동의
        </legend>

        <div className="rounded-2xl border border-purple-200 bg-purple-50/20 px-4 py-2">
          <Checkbox
            emphasized
            checked={allChecked}
            onChange={(checked) =>
              onChange({
                service: checked,
                privacy: checked,
                marketing: checked,
              })
            }
          >
            전체 동의
          </Checkbox>

          <div className="my-1 h-px bg-purple-100" />

          {ITEMS.map(({ key, required, title }) => (
            <div key={key} className="flex items-center justify-between">
              <Checkbox
                checked={value[key]}
                onChange={(checked) => onChange({ ...value, [key]: checked })}
              >
                <span className="font-semibold text-purple-500">
                  {required ? "(필수)" : "(선택)"}
                </span>{" "}
                <span>{title}</span>
              </Checkbox>
              <button
                type="button"
                onClick={() => setModalDoc(TERMS_DOCUMENTS[key])}
                className="rounded p-1 text-purple-400 hover:text-purple-600 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
                aria-label={`${title} 내용 보기`}
              >
                <CaretRightIcon size={16} aria-hidden />
              </button>
            </div>
          ))}
        </div>
      </fieldset>

      <TermsModal
        isOpen={Boolean(modalDoc)}
        onClose={() => setModalDoc(null)}
        title={modalDoc?.title ?? ""}
        content={modalDoc?.content ?? ""}
      />
    </>
  );
}
