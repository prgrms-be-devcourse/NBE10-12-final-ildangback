import lockIcon from "../../../assets/icons/image_102.webp";
import leftCharacter from "../../../assets/icons/image_103.webp";
import rightCharacter from "../../../assets/icons/image_104.webp";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TopBar } from "../../../shared/ui/TopBar";
import { joinByCode } from "../api";

const schema = z.object({
  inviteCode: z
    .string()
    .regex(
      /^[A-Z0-9]{6}$/,
      "영문 대문자와 숫자로 된 6자리 코드를 입력해주세요.",
    ),
});
export function GroupJoinByCodePage() {
  const [params] = useSearchParams();
  const code = (params.get("code") ?? "").toUpperCase();
  const initialCode = /^[A-Z0-9]{6}$/.test(code) ? code : "";
  return <GroupJoinByCodeForm key={initialCode} initialCode={initialCode} />;
}

function GroupJoinByCodeForm({ initialCode }: { initialCode: string }) {
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { inviteCode: initialCode },
  });
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const lock = useRef(false);
  const [digits, setDigits] = useState<string[]>(() =>
    initialCode ? initialCode.split("") : Array(6).fill(""),
  );
  const inputs = useRef<(HTMLInputElement | null)[]>([]);
  const updateDigits = (next: string[]) => {
    setDigits(next);
    form.setValue("inviteCode", next.join(""), {
      shouldDirty: true,
      shouldValidate: form.formState.isSubmitted,
    });
  };
  const distribute = (value: string, index: number) => {
    const chars = value
      .toUpperCase()
      .replace(/[^A-Z0-9]/g, "")
      .slice(0, 6);
    if (!chars) return;
    const start = chars.length === 6 ? 0 : index;
    const next = [...digits];
    for (
      let offset = 0;
      offset < chars.length && start + offset < 6;
      offset++
    ) {
      next[start + offset] = chars[offset];
    }
    updateDigits(next);
    inputs.current[Math.min(start + chars.length, 5)]?.focus();
  };
  const navigate = useNavigate();
  const { showToast } = useToast();
  const submit = async (values: z.infer<typeof schema>) => {
    if (lock.current) return;
    lock.current = true;
    setPending(true);
    setError(null);
    try {
      const result = await joinByCode(values);
      showToast("그룹에 참여했어요.");
      navigate(`/challenges/${result.challengeId}`, { replace: true });
    } catch (err) {
      setError(
        applyApiError(err, form.setError, {
          fields: ["inviteCode"],
          toField: { INVITE_CODE_NOT_FOUND: "inviteCode" },
        }),
      );
    } finally {
      lock.current = false;
      setPending(false);
    }
  };
  return (
    <>
      <TopBar title="초대코드로 참여" />
      <main className="px-6 pt-10 pb-10">
        <div
          className="mx-auto mt-8 mb-7 flex items-end justify-center gap-3"
          aria-hidden="true"
        >
          <img src={leftCharacter} alt="" className="h-14 w-8 object-contain" />
          <img src={lockIcon} alt="" className="h-24 w-24 object-contain" />
          <img
            src={rightCharacter}
            alt=""
            className="h-14 w-8 object-contain"
          />
        </div>
        <h1 className="text-center text-2xl font-bold">
          초대 코드를 입력해주세요
        </h1>
        <p className="mt-3 text-center text-sm leading-relaxed text-gray-500">
          친구에게 받은 6자리 코드로 비공개 그룹에 참여할 수 있어요.
        </p>
        <form
          className="mt-8 space-y-5"
          noValidate
          onSubmit={(event) => void form.handleSubmit(submit)(event)}
        >
          <input type="hidden" {...form.register("inviteCode")} />
          <fieldset disabled={pending}>
            <legend className="sr-only">6자리 초대코드</legend>
            <div className="mx-auto grid w-full max-w-sm grid-cols-6 gap-2">
              {digits.map((digit, index) => (
                <input
                  key={index}
                  ref={(element) => {
                    inputs.current[index] = element;
                  }}
                  aria-label={`초대코드 ${index + 1}번째 자리`}
                  aria-invalid={!!form.formState.errors.inviteCode}
                  aria-describedby={
                    form.formState.errors.inviteCode
                      ? "invite-code-error"
                      : undefined
                  }
                  value={digit}
                  maxLength={1}
                  autoCapitalize="characters"
                  autoComplete="off"
                  spellCheck={false}
                  className="aspect-square w-full min-w-0 rounded-xl border border-purple-200 bg-purple-50/50 text-center text-xl font-bold text-purple-700 outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-100 disabled:opacity-50"
                  onFocus={(event) => event.currentTarget.select()}
                  onChange={(event) => {
                    if (!event.target.value) {
                      const next = [...digits];
                      next[index] = "";
                      updateDigits(next);
                    } else distribute(event.target.value, index);
                  }}
                  onPaste={(event) => {
                    event.preventDefault();
                    distribute(event.clipboardData.getData("text"), index);
                  }}
                  onKeyDown={(event) => {
                    if (event.key === "Backspace" && !digit && index > 0) {
                      event.preventDefault();
                      inputs.current[index - 1]?.focus();
                    } else if (event.key === "ArrowLeft" && index > 0) {
                      event.preventDefault();
                      inputs.current[index - 1]?.focus();
                    } else if (event.key === "ArrowRight" && index < 5) {
                      event.preventDefault();
                      inputs.current[index + 1]?.focus();
                    }
                  }}
                />
              ))}
            </div>
            {form.formState.errors.inviteCode && (
              <p
                id="invite-code-error"
                role="alert"
                className="mt-2 text-sm text-red-600"
              >
                {form.formState.errors.inviteCode.message}
              </p>
            )}
          </fieldset>
          <p className="rounded-xl bg-purple-50 p-3 text-xs leading-relaxed text-purple-700">
            참여하기를 누르면 코드에 해당하는 그룹에 바로 참여합니다.
          </p>
          <FormAlert message={error} />
          <Button type="submit" loading={pending}>
            코드로 참여하기
          </Button>
        </form>
      </main>
    </>
  );
}
