import lockIcon from "../../../assets/icons/fluent_lock_closed_key_16_regular.webp";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
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
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { inviteCode: "" },
  });
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const lock = useRef(false);
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
        <img
          src={lockIcon}
          alt=""
          width={72}
          height={72}
          className="mx-auto my-6 h-18 w-18 object-contain"
        />
        <h1 className="text-center text-2xl font-bold">
          초대코드를 입력해주세요
        </h1>
        <p className="mt-3 text-center text-sm leading-relaxed text-gray-500">
          친구에게 받은 6자리 코드로 비공개 그룹에 참여해요.
        </p>
        <form
          className="mt-8 space-y-5"
          noValidate
          onSubmit={(event) => void form.handleSubmit(submit)(event)}
        >
          <TextField
            label="초대코드"
            maxLength={6}
            autoCapitalize="characters"
            autoComplete="off"
            spellCheck={false}
            placeholder="6자리 코드"
            error={form.formState.errors.inviteCode?.message}
            {...form.register("inviteCode", {
              onChange: (event) =>
                form.setValue("inviteCode", event.target.value.toUpperCase()),
            })}
          />
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
