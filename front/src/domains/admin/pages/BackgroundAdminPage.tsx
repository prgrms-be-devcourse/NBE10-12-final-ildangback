import { TrashIcon } from "@phosphor-icons/react";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../../shared/api/client";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { TopBar } from "../../../shared/ui/TopBar";
import {
  createBackground,
  deleteBackground,
  getAllBackgrounds,
} from "../../background/api";
import {
  MAP_TYPE_LABEL,
  type BackgroundResponse,
  type MapType,
} from "../../background/types";

const MAP_TYPES: MapType[] = ["STUDY_ROOM", "GYM"];

const NAME_MAX = 50;

/** 관리자 > 그룹 배경. 등록(멀티파트)과 목록과 삭제를 한 화면에서 한다. */
export function BackgroundAdminPage() {
  const [backgrounds, setBackgrounds] = useState<BackgroundResponse[] | null>(
    null,
  );
  const [listError, setListError] = useState<string | null>(null);

  const reload = useCallback(() => {
    getAllBackgrounds()
      .then((next) => {
        setBackgrounds(next);
        setListError(null);
      })
      .catch((e) => {
        setBackgrounds([]);
        setListError(errorMessage(e));
      });
  }, []);

  useEffect(reload, [reload]);

  return (
    <>
      <TopBar title="그룹 배경 관리" />

      <div className="flex-1 px-5 pb-10">
        <CreateBackgroundForm onCreated={reload} />

        <h2 className="mt-8 mb-3 text-[15px] font-bold text-gray-900">
          등록된 배경{backgrounds ? ` ${backgrounds.length}개` : ""}
        </h2>

        <FormAlert message={listError} />

        {backgrounds === null ? (
          <p className="py-8 text-center text-[14px] text-gray-400">
            불러오는 중…
          </p>
        ) : backgrounds.length === 0 ? (
          <p className="py-8 text-center text-[14px] text-gray-400">
            등록된 배경이 없어요
          </p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {backgrounds.map((background) => (
              <BackgroundRow
                key={background.backgroundId}
                background={background}
                onDeleted={reload}
              />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function CreateBackgroundForm({ onCreated }: { onCreated: () => void }) {
  const [mapType, setMapType] = useState<MapType>("STUDY_ROOM");
  const [name, setName] = useState("");
  const [price, setPrice] = useState("0");
  const [image, setImage] = useState<{ file: File; url: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const imageRef = useRef(image);
  useEffect(() => {
    imageRef.current = image;
  });
  useEffect(
    () => () => {
      if (imageRef.current) URL.revokeObjectURL(imageRef.current.url);
    },
    [],
  );

  const pick = (file: File | undefined) => {
    const previous = image;
    setImage(file ? { file, url: URL.createObjectURL(file) } : null);
    if (previous) URL.revokeObjectURL(previous.url);
  };

  const priceNumber = Number(price);
  const priceValid =
    price !== "" && Number.isInteger(priceNumber) && priceNumber >= 0;
  const canSubmit =
    name.trim() !== "" &&
    name.trim().length <= NAME_MAX &&
    priceValid &&
    image !== null;

  const submit = async () => {
    if (!canSubmit || saving || !image) return;
    setSaving(true);
    setError(null);
    try {
      await createBackground({
        mapType,
        name: name.trim(),
        price: priceNumber,
        image: image.file,
      });
      setName("");
      setPrice("0");
      pick(undefined);
      onCreated();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="mt-2 rounded-2xl border border-purple-200 p-4">
      <h2 className="text-[15px] font-bold text-gray-900">배경 등록</h2>

      <fieldset className="mt-4">
        <legend className="text-[13px] font-semibold text-gray-700">
          맵 타입
        </legend>
        <div className="mt-2 flex gap-2">
          {MAP_TYPES.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={mapType === value}
              onClick={() => setMapType(value)}
              className={`rounded-full border px-3 py-1.5 text-[13px] ${
                mapType === value
                  ? "border-purple-500 bg-purple-50 font-semibold text-purple-700"
                  : "border-purple-200 bg-white text-gray-500"
              }`}
            >
              {MAP_TYPE_LABEL[value]}
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
          이미지
        </legend>
        <div className="mt-2 flex items-center gap-3">
          {image ? (
            <img
              src={image.url}
              alt="배경 미리보기"
              className="size-16 shrink-0 rounded-lg bg-purple-50 object-cover"
            />
          ) : (
            <span
              aria-hidden
              className="size-16 shrink-0 rounded-lg border border-dashed border-purple-200"
            />
          )}
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp"
            onChange={(e) => pick(e.target.files?.[0])}
            className="min-w-0 flex-1 text-[12px] text-gray-600"
          />
        </div>
      </fieldset>

      <div className="mt-4 space-y-3">
        <FormAlert message={error} />
        <Button onClick={submit} disabled={!canSubmit} loading={saving}>
          {saving ? "등록 중…" : "등록"}
        </Button>
      </div>
    </section>
  );
}

function BackgroundRow({
  background,
  onDeleted,
}: {
  background: BackgroundResponse;
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
      await deleteBackground(background.backgroundId);
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
        <img
          src={background.imageUrl}
          alt=""
          aria-hidden
          className="size-12 shrink-0 rounded-lg bg-purple-50 object-cover"
        />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[14px] font-semibold text-gray-900">
            {background.name}
          </p>
          <p className="text-[12px] text-gray-500">
            {MAP_TYPE_LABEL[background.mapType]} / {background.price}P
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
            aria-label={`${background.name} 삭제`}
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
