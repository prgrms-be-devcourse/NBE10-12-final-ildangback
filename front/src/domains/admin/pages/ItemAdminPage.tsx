import { TrashIcon } from "@phosphor-icons/react";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../../shared/api/client";
import type { ItemResponse, ItemSlot } from "../../../shared/api/types";
import { AuthedImage } from "../../../shared/ui/AuthedImage";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { TopBar } from "../../../shared/ui/TopBar";
import { createItem, deleteItem, getAllItems } from "../../item/api";
import { CharacterRenderer } from "../../user/components/CharacterRenderer";
import type { CharacterPose, CharacterSlots } from "../../user/types";

const SLOTS: ItemSlot[] = ["HEAD", "TOP", "BOTTOM", "SHOES"];
const SLOT_LABEL: Record<ItemSlot, string> = {
  HEAD: "머리",
  TOP: "상의",
  BOTTOM: "하의",
  SHOES: "신발",
};

// DEFAULT 가 맨 앞이다. 서버가 필수로 요구하는 포즈라 화면에서도 먼저 보여준다.
const POSES: { pose: CharacterPose; label: string }[] = [
  { pose: "DEFAULT", label: "기본" },
  { pose: "GYM_SUCCESS", label: "헬스장 성공" },
  { pose: "GYM_FAIL", label: "헬스장 실패" },
  { pose: "STUDY_SUCCESS", label: "공부방 성공" },
  { pose: "STUDY_FAIL", label: "공부방 실패" },
];

const NAME_MAX = 50;

const EMPTY_SLOTS: CharacterSlots = {
  HEAD: null,
  TOP: null,
  BOTTOM: null,
  SHOES: null,
};

/** 고른 파일 하나. url 은 미리보기용 objectURL 이라 바꿀 때와 떠날 때 revoke 한다. */
interface PickedImage {
  file: File;
  url: string;
}

/** 관리자 > 아이템. 등록(멀티파트)과 목록과 삭제를 한 화면에서 한다. */
export function ItemAdminPage() {
  const [items, setItems] = useState<ItemResponse[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);

  // 상태 변경은 전부 promise 안에서 한다. 효과 본문에서 동기로 setState 하면
  // react-hooks/set-state-in-effect 에 걸린다.
  const reload = useCallback(() => {
    getAllItems()
      .then((next) => {
        setItems(next);
        setListError(null);
      })
      .catch((e) => {
        setItems([]);
        setListError(errorMessage(e));
      });
  }, []);

  useEffect(reload, [reload]);

  return (
    <>
      <TopBar title="아이템 관리" />

      <div className="flex-1 px-5 pb-10">
        <CreateItemForm onCreated={reload} />

        <h2 className="mt-8 mb-3 text-[15px] font-bold text-gray-900">
          등록된 아이템{items ? ` ${items.length}개` : ""}
        </h2>

        <FormAlert message={listError} />

        {items === null ? (
          <p className="py-8 text-center text-[14px] text-gray-400">
            불러오는 중…
          </p>
        ) : items.length === 0 ? (
          <p className="py-8 text-center text-[14px] text-gray-400">
            등록된 아이템이 없어요
          </p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {items.map((item) => (
              <ItemRow key={item.id} item={item} onDeleted={reload} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function CreateItemForm({ onCreated }: { onCreated: () => void }) {
  const [slot, setSlot] = useState<ItemSlot>("HEAD");
  const [name, setName] = useState("");
  const [price, setPrice] = useState("0");
  const [files, setFiles] = useState<
    Partial<Record<CharacterPose, PickedImage>>
  >({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // 최신 files 를 언마운트 정리에서 읽기 위한 사본. effect 가 files 를 의존하면
  // 파일을 고를 때마다 정리가 돌아 방금 만든 objectURL 을 되돌린다.
  const filesRef = useRef(files);
  useEffect(() => {
    filesRef.current = files;
  });
  useEffect(
    () => () => {
      for (const picked of Object.values(filesRef.current)) {
        if (picked) URL.revokeObjectURL(picked.url);
      }
    },
    [],
  );

  // objectURL 은 갱신 함수 밖에서 만든다. StrictMode 가 갱신 함수를 두 번 부르면
  // 하나가 revoke 되지 않고 남는다.
  const pick = (pose: CharacterPose, file: File | undefined) => {
    const previous = files[pose];
    const next = file ? { file, url: URL.createObjectURL(file) } : undefined;
    setFiles((prev) => {
      const copy = { ...prev };
      if (next) copy[pose] = next;
      else delete copy[pose];
      return copy;
    });
    if (previous) URL.revokeObjectURL(previous.url);
  };

  const clearFiles = () => {
    for (const picked of Object.values(files)) {
      if (picked) URL.revokeObjectURL(picked.url);
    }
    setFiles({});
  };

  const picked = POSES.filter(({ pose }) => files[pose]);
  const priceNumber = Number(price);
  const priceValid =
    price !== "" && Number.isInteger(priceNumber) && priceNumber >= 0;
  // 서버 검증과 같은 조건이다 - 이름, 0 이상 가격, DEFAULT 포함 이미지 한 장 이상.
  const canSubmit =
    name.trim() !== "" &&
    name.trim().length <= NAME_MAX &&
    priceValid &&
    files.DEFAULT !== undefined;

  const submit = async () => {
    if (!canSubmit || saving) return;
    setSaving(true);
    setError(null);
    try {
      await createItem({
        slot,
        name: name.trim(),
        price: priceNumber,
        images: picked.map(({ pose }) => ({ pose, file: files[pose]!.file })),
      });
      setName("");
      setPrice("0");
      clearFiles();
      onCreated();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="mt-2 rounded-2xl border border-purple-200 p-4">
      <h2 className="text-[15px] font-bold text-gray-900">아이템 등록</h2>

      <fieldset className="mt-4">
        <legend className="text-[13px] font-semibold text-gray-700">
          부위
        </legend>
        <div className="mt-2 flex gap-2">
          {SLOTS.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={slot === value}
              onClick={() => setSlot(value)}
              className={`rounded-full border px-3 py-1.5 text-[13px] ${
                slot === value
                  ? "border-purple-500 bg-purple-50 font-semibold text-purple-700"
                  : "border-purple-200 bg-white text-gray-500"
              }`}
            >
              {SLOT_LABEL[value]}
            </button>
          ))}
        </div>
      </fieldset>

      <TextField
        label="이름"
        className="mt-4"
        value={name}
        maxLength={NAME_MAX}
        counter={`${name.length}/${NAME_MAX}`}
        onChange={(e) => setName(e.target.value)}
      />

      <TextField
        label="가격"
        className="mt-3"
        type="number"
        min={0}
        step={1}
        value={price}
        error={priceValid ? undefined : "0 이상의 정수여야 해요"}
        onChange={(e) => setPrice(e.target.value)}
      />

      <fieldset className="mt-4">
        <legend className="text-[13px] font-semibold text-gray-700">
          포즈별 이미지
        </legend>
        <p className="mt-1 text-[12px] text-gray-500">
          기본은 반드시 넣어야 해요. 나머지는 넣은 것만 등록됩니다. 고르면 그
          포즈 캐릭터가 입은 모습으로 보여줍니다.
        </p>
        <div className="mt-3 space-y-3">
          {POSES.map(({ pose, label }) => {
            const image = files[pose];
            return (
              <label key={pose} className="flex items-center gap-3">
                {/* 미리보기 자리는 파일이 없어도 잡아 둔다. 고를 때 줄이 밀리지 않게. */}
                {image ? (
                  <CharacterRenderer
                    pose={pose}
                    slots={{ ...EMPTY_SLOTS, [slot]: image.url }}
                    label={`${label} 미리보기`}
                    className="size-16 shrink-0 rounded-lg bg-purple-50"
                  />
                ) : (
                  <span
                    aria-hidden
                    className="size-16 shrink-0 rounded-lg border border-dashed border-purple-200"
                  />
                )}
                <span className="min-w-0 flex-1">
                  <span className="block text-[13px] text-gray-700">
                    {label}
                    {pose === "DEFAULT" && (
                      <span className="text-red-500"> *</span>
                    )}
                  </span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => pick(pose, e.target.files?.[0])}
                    className="mt-1 w-full text-[12px] text-gray-600"
                  />
                </span>
              </label>
            );
          })}
        </div>
      </fieldset>

      <div className="mt-4 space-y-3">
        <FormAlert message={error} />
        <Button onClick={submit} disabled={!canSubmit} loading={saving}>
          {saving ? "등록 중…" : `등록 (이미지 ${picked.length}장)`}
        </Button>
      </div>
    </section>
  );
}

function ItemRow({
  item,
  onDeleted,
}: {
  item: ItemResponse;
  onDeleted: () => void;
}) {
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  const remove = async () => {
    if (deleting) return;
    setDeleting(true);
    setError(null);
    try {
      await deleteItem(item.id);
      onDeleted();
    } catch (e) {
      setError(errorMessage(e));
      setDeleting(false);
      setConfirming(false);
    }
  };

  return (
    <li className="py-3">
      <div className="flex items-center gap-3">
        <div className="size-12 shrink-0 overflow-hidden rounded-lg bg-purple-50">
          <AuthedImage
            src={item.imageUrl}
            alt=""
            aria-hidden
            className="size-full object-contain"
          />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[14px] font-semibold text-gray-900">
            {item.name}
          </p>
          <p className="text-[12px] text-gray-500">
            {SLOT_LABEL[item.slot]} / {item.price}P
          </p>
        </div>

        {confirming ? (
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={remove}
              disabled={deleting}
              className="rounded-lg bg-red-50 px-3 py-1.5 text-[13px] font-semibold text-red-600 disabled:opacity-60"
            >
              {deleting ? "삭제 중…" : "삭제"}
            </button>
            <button
              type="button"
              onClick={() => setConfirming(false)}
              className="rounded-lg px-2 py-1.5 text-[13px] text-gray-500"
            >
              취소
            </button>
          </div>
        ) : (
          <button
            type="button"
            aria-label={`${item.name} 삭제`}
            onClick={() => setConfirming(true)}
            className="shrink-0 rounded-lg p-2 text-gray-400 hover:bg-red-50 hover:text-red-600"
          >
            <TrashIcon size={18} />
          </button>
        )}
      </div>
      {error && <p className="mt-2 text-[12px] text-red-600">{error}</p>}
    </li>
  );
}

// 서버 message 를 그대로 띄운다. 프론트에서 code 를 문구로 매핑하면 백엔드가 문구를
// 고칠 때 어긋난다 (applyApiError 와 같은 규칙).
function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  return "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
}
