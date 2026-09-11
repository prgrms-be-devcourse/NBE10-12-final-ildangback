import {
  CaretDownIcon,
  CaretLeftIcon,
  CaretRightIcon,
  CheckIcon,
} from "@phosphor-icons/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { equipItem, fetchShop, purchaseItem, unequipItem } from "../api";
import {
  CHARACTER_LAYERS,
  OWNERSHIP_FILTERS,
  OWNERSHIP_LABEL,
  type OwnershipFilter,
  type ShopData,
  type ShopItem,
  SLOT_LABEL,
  SLOTS,
  type Sort,
  SORT_LABEL,
  SORTS,
} from "../lib/shop";
import type { ItemSlot } from "../../../shared/api/types";
import { CharacterView } from "../components/CharacterView";
import { ItemThumb } from "../components/ItemThumb";
import { PurchaseDialog } from "../components/PurchaseDialog";
import { objectParticle } from "../../../shared/lib/korean";
import { useToast } from "../../../shared/lib/useToast";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { designArt } from "../../../shared/ui/designArt";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { TopBar } from "../../../shared/ui/TopBar";

// 시안 393px 프레임 값이다.
const DEEP = "#774AD3";
const CARD = "#FEFEFE";
const PILL = "#F6F1FF";
const TAB_OFF = "#FBF8FD";
const TAB_OFF_TEXT = "#8F80C5";
const TRACK = "#F6F6FA";
const CHIP_ON = "#ECE7F6";
const CHIP_OFF_TEXT = "#7A7697";
const SELECT_TEXT = "#6E7888";
const NAME = "#4A4A4A";
const BADGE_OWNED = "#EDE8F5";
const BADGE_PLAIN = "#F6F4F9";

export function CharacterShopPage() {
  const { showToast } = useToast();

  const [slot, setSlot] = useState<ItemSlot>("HEAD");
  const [ownership, setOwnership] = useState<OwnershipFilter>("ALL");
  const [sort, setSort] = useState<Sort>("DEFAULT");

  const [shop, setShop] = useState<ShopData | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  // 누른 아이템. 누르면 바로 사거나 입지 않고 아래 바에서 한 번 더 고른다.
  const [pickedId, setPickedId] = useState<number | null>(null);
  // 확인 창에 올라간 아이템. null 이면 창이 닫혀 있다.
  const [confirming, setConfirming] = useState<ShopItem | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchShop()
      .then((data) => {
        if (cancelled) return;
        setShop(data);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const items = useMemo(() => {
    const filtered = (shop?.items ?? []).filter(
      (item) =>
        item.slot === slot &&
        (ownership === "ALL" ||
          (ownership === "OWNED" ? item.owned : !item.owned)),
    );

    return [...filtered].sort((a, b) => {
      if (sort === "CHEAP") return a.price - b.price;
      if (sort === "EXPENSIVE") return b.price - a.price;
      // 기본순은 서버가 준 등록순 그대로다.
      return 0;
    });
  }, [shop, slot, ownership, sort]);

  const picked = shop?.items.find((i) => i.id === pickedId) ?? null;
  const equipped = shop?.equipped ?? {};

  // 착용 중인 부위의 그림. 카탈로그의 imageUrl 이 DEFAULT 포즈 그림이라
  // /api/users/me/character 를 따로 부르지 않아도 같은 URL 이 나온다.
  const characterArt = useMemo(() => {
    const art: Partial<Record<ItemSlot, string>> = {};
    for (const slot of CHARACTER_LAYERS) {
      const itemId = shop?.equipped[slot];
      const url = shop?.items.find((i) => i.id === itemId)?.imageUrl;
      if (url) art[slot] = url;
    }
    return art;
  }, [shop]);

  async function buy(item: ShopItem) {
    setBusy(true);
    try {
      // 서버가 구매와 함께 같은 부위를 벗기고 새 아이템을 입힌다.
      // 여기서 착용을 또 부르면 ALREADY_EQUIPPED 로 실패한다.
      const bought = await purchaseItem(item.id);

      setShop((prev) =>
        prev
          ? {
              ...prev,
              point: bought.balance,
              equipped: bought.equippedSlot
                ? { ...prev.equipped, [bought.equippedSlot]: item.id }
                : prev.equipped,
              items: prev.items.map((i) =>
                i.id === item.id
                  ? { ...i, owned: true, userItemId: bought.userItemId }
                  : i,
              ),
            }
          : prev,
      );
      setConfirming(null);
      showToast(
        `${item.name}${objectParticle(item.name)} 구매하고 바로 착용했어요.`,
      );
    } catch (error) {
      showToast(error instanceof Error ? error.message : "구매하지 못했어요.");
    } finally {
      setBusy(false);
    }
  }

  async function wear(item: ShopItem, take: boolean) {
    // 보유 중이어야 버튼이 뜨므로 userItemId 는 항상 있다.
    if (item.userItemId === null) return;

    setBusy(true);
    try {
      // 어느 부위에 들어갔는지는 응답이 알려준다. 벗으면 null 로 온다.
      const result = take
        ? await unequipItem(item.userItemId)
        : await equipItem(item.userItemId);

      setShop((prev) => {
        if (!prev) return prev;
        const equipped = { ...prev.equipped };
        // 서버가 같은 부위의 이전 아이템을 알아서 벗긴다.
        if (result.equippedSlot) equipped[result.equippedSlot] = item.id;
        else delete equipped[item.slot];
        return { ...prev, equipped };
      });
    } catch (error) {
      showToast(error instanceof Error ? error.message : "적용하지 못했어요.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <>
        <TopBar title="개인 상점" />
        <p className="py-20 text-center text-[13px] text-gray-500">
          불러오는 중…
        </p>
      </>
    );
  }

  if (failed || !shop) {
    return (
      <>
        <TopBar title="개인 상점" />
        <p className="py-20 text-center text-[13px] text-gray-500">
          상점을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
      </>
    );
  }

  return (
    <>
      <div className="flex min-h-full flex-col">
        <TopBar title="개인 상점" />

        <div className="grow px-[22px] pb-10">
          <section
            className="relative mt-[18px] h-[188px] overflow-hidden rounded-[10px] border border-purple-200"
            style={{ backgroundColor: CARD }}
          >
            <img
              src={designArt.shopRoom}
              alt=""
              className="h-full w-full object-cover pixelated"
              aria-hidden
            />

            {/* 아이템 그림이 1080x1080 정사각 캔버스라 상자도 정사각이어야 겹쳐진다.
                배경판 받침대에 발이 닿도록 맞춰 뒀다. */}
            <span className="absolute bottom-[1px] left-1/2 block h-[174px] w-[174px] -translate-x-1/2">
              <CharacterView
                art={characterArt}
                label={characterLabel(shop.items, equipped)}
              />
            </span>

            <span
              className="absolute top-[8px] right-[6px] flex h-[27px] w-[78px] items-center justify-center gap-[5px] rounded-full"
              style={{ backgroundColor: PILL }}
            >
              <PixelIcon src={pixelIcons.pointHistory} size={15} />
              <span className="text-[14px] leading-none font-bold text-purple-500">
                {shop.point.toLocaleString()}P
              </span>
            </span>

            <button
              type="button"
              onClick={() =>
                showToast("캐릭터 회전은 다음 업데이트에 오픈됩니다.")
              }
              aria-label="캐릭터 왼쪽으로 돌리기"
              className="absolute top-1/2 left-[8px] flex h-[28px] w-[28px] -translate-y-1/2 items-center justify-center rounded-full bg-white/70 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
            >
              <CaretLeftIcon size={16} weight="bold" color={DEEP} aria-hidden />
            </button>
            <button
              type="button"
              onClick={() =>
                showToast("캐릭터 회전은 다음 업데이트에 오픈됩니다.")
              }
              aria-label="캐릭터 오른쪽으로 돌리기"
              className="absolute top-1/2 right-[8px] flex h-[28px] w-[28px] -translate-y-1/2 items-center justify-center rounded-full bg-white/70 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
            >
              <CaretRightIcon
                size={16}
                weight="bold"
                color={DEEP}
                aria-hidden
              />
            </button>
          </section>

          <div
            role="tablist"
            aria-label="아이템 부위"
            className="mt-[18px] grid grid-cols-4 gap-[10px]"
          >
            {SLOTS.map((key) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={slot === key}
                onClick={() => setSlot(key)}
                className="h-[31px] rounded-[6px] text-[12px] font-semibold"
                style={
                  slot === key
                    ? { backgroundColor: DEEP, color: "#fff" }
                    : {
                        backgroundColor: TAB_OFF,
                        color: TAB_OFF_TEXT,
                        border: "1px solid var(--color-purple-200)",
                      }
                }
              >
                {SLOT_LABEL[key]}
              </button>
            ))}
          </div>

          <div className="mt-[11px] flex items-center justify-between">
            <div
              className="flex h-[26px] w-[164px] items-center rounded-[6px] p-px"
              style={{ backgroundColor: TRACK }}
            >
              {OWNERSHIP_FILTERS.map((key) => (
                <button
                  key={key}
                  type="button"
                  aria-pressed={ownership === key}
                  onClick={() => setOwnership(key)}
                  className="h-[24px] flex-1 rounded-[5.5px] text-[11px] font-semibold"
                  style={
                    ownership === key
                      ? {
                          backgroundColor: CHIP_ON,
                          color: DEEP,
                          border: "1px solid var(--color-purple-200)",
                        }
                      : { color: CHIP_OFF_TEXT }
                  }
                >
                  {OWNERSHIP_LABEL[key]}
                </button>
              ))}
            </div>

            <SortSelect value={sort} onChange={setSort} />
          </div>

          {items.length === 0 ? (
            <p className="py-16 text-center text-[12px] text-gray-500">
              조건에 맞는 아이템이 없어요.
            </p>
          ) : (
            <ul className="mt-[12px] grid grid-cols-3 gap-x-[14px] gap-y-[15px]">
              {items.map((item) => (
                <li key={item.id}>
                  <ItemCard
                    item={item}
                    worn={equipped[item.slot] === item.id}
                    picked={pickedId === item.id}
                    onSelect={() =>
                      setPickedId(pickedId === item.id ? null : item.id)
                    }
                  />
                </li>
              ))}
            </ul>
          )}
        </div>

        {picked && (
          <ActionBar
            item={picked}
            point={shop.point}
            worn={equipped[picked.slot] === picked.id}
            busy={busy}
            onBuy={() => setConfirming(picked)}
            onWear={(take) => wear(picked, take)}
          />
        )}
      </div>

      <PurchaseDialog
        item={confirming}
        point={shop.point}
        busy={busy}
        onConfirm={() => confirming && buy(confirming)}
        onClose={() => !busy && setConfirming(null)}
      />
    </>
  );
}

/** 미리보기는 그림만 겹친 것이라 무엇을 입고 있는지 말로 한 번 더 알린다. */
function characterLabel(
  items: ShopItem[],
  equipped: ShopData["equipped"],
): string {
  const worn = CHARACTER_LAYERS.map(
    (slot) => items.find((i) => i.id === equipped[slot])?.name,
  ).filter((name): name is string => Boolean(name));

  if (worn.length === 0) return "아무것도 착용하지 않은 캐릭터";

  const names = worn.join(", ");
  return `${names}${objectParticle(names)} 착용한 캐릭터`;
}

/**
 * 고른 아이템에 대해 할 수 있는 일을 한 줄로 보여준다. 시안 참고 화면에도
 * 같은 자리에 있다. 카드를 눌렀을 때 바로 사지 않고 여기서 한 번 더 고르게
 * 하는 이유는 포인트가 되돌릴 수 없이 빠지기 때문이다.
 */
function ActionBar({
  item,
  point,
  worn,
  busy,
  onBuy,
  onWear,
}: {
  item: ShopItem;
  point: number;
  worn: boolean;
  busy: boolean;
  onBuy(): void;
  onWear(take: boolean): void;
}) {
  const short = item.price - point;
  const canBuy = short <= 0;

  return (
    <div
      className="sticky bottom-0 z-20 border-t border-purple-200 px-[22px] py-[12px]"
      style={{ backgroundColor: CARD }}
    >
      <div className="flex items-center gap-[10px]">
        <span className="block h-[44px] w-[44px] shrink-0 overflow-hidden rounded-[10px] bg-purple-50">
          <ItemThumb item={item} />
        </span>

        <span className="min-w-0 flex-1">
          <span className="block truncate text-[13px] font-semibold text-gray-900">
            {item.name}
          </span>
          <span className="mt-[3px] flex items-center gap-[4px] text-[11px]">
            {item.owned ? (
              <span style={{ color: DEEP }}>보유 중</span>
            ) : (
              <>
                <PixelIcon src={pixelIcons.pointHistory} size={12} />
                <span className="font-semibold text-gray-900 tabular-nums">
                  {item.price.toLocaleString()}P
                </span>
                {!canBuy && (
                  <span className="text-[#D94B2B]">
                    · {short.toLocaleString()}P 모자라요
                  </span>
                )}
              </>
            )}
          </span>
        </span>

        {item.owned ? (
          <button
            type="button"
            onClick={() => onWear(worn)}
            disabled={busy}
            className="h-[40px] w-[92px] shrink-0 rounded-[10px] text-[13px] font-semibold focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:opacity-50"
            style={
              worn
                ? { border: "1px solid var(--color-purple-200)", color: DEEP }
                : { backgroundColor: DEEP, color: "#fff" }
            }
          >
            {worn ? "해제하기" : "착용하기"}
          </button>
        ) : (
          <button
            type="button"
            onClick={onBuy}
            disabled={!canBuy || busy}
            className="h-[40px] w-[92px] shrink-0 rounded-[10px] text-[13px] font-semibold text-white focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-40"
            style={{ backgroundColor: DEEP }}
          >
            구매하기
          </button>
        )}
      </div>
    </div>
  );
}

function ItemCard({
  item,
  worn,
  picked,
  onSelect,
}: {
  item: ShopItem;
  worn: boolean;
  picked: boolean;
  onSelect(): void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={picked}
      className="relative h-[115px] w-full rounded-[10px] focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
      style={{
        backgroundColor: CARD,
        // 고른 것은 테두리를 두껍게 해서 착용 중(가는 테두리)과 구분한다.
        border: picked
          ? `2px solid ${DEEP}`
          : `1px solid ${worn ? DEEP : "var(--color-purple-200)"}`,
      }}
    >
      {worn && (
        <span
          className="absolute top-[5px] right-[5px] flex h-[15px] w-[15px] items-center justify-center rounded-full"
          style={{ backgroundColor: DEEP }}
        >
          <CheckIcon size={9} weight="bold" color="#fff" aria-hidden />
        </span>
      )}

      <span className="absolute top-[14px] left-1/2 block h-[52px] w-[52px] -translate-x-1/2">
        <ItemThumb item={item} />
      </span>

      <span
        className="absolute top-[66px] left-0 block w-full truncate px-2 text-[11px]"
        style={{ color: NAME }}
      >
        {item.name}
      </span>

      <span
        className="absolute bottom-[9px] left-[15px] flex h-[20px] w-[77px] items-center justify-center gap-[3px] rounded-[5.5px] border border-purple-200 text-[11px] font-semibold"
        style={{
          backgroundColor: item.owned ? BADGE_OWNED : BADGE_PLAIN,
          color: item.owned ? DEEP : "#3C3C3C",
        }}
      >
        {item.owned ? (
          worn ? (
            "착용 중"
          ) : (
            "보유 중"
          )
        ) : (
          <>
            <PixelIcon src={pixelIcons.pointHistory} size={11} />
            {item.price}P
          </>
        )}
      </span>
    </button>
  );
}

function SortSelect({
  value,
  onChange,
}: {
  value: Sort;
  onChange(value: Sort): void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  return (
    <div ref={rootRef} className="relative shrink-0">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-[25px] w-[71px] items-center justify-center gap-1 rounded-[5.5px] border border-purple-200 text-[11px]"
        style={{ color: SELECT_TEXT }}
      >
        {SORT_LABEL[value]}
        <CaretDownIcon
          size={12}
          className={open ? "rotate-180" : ""}
          aria-hidden
        />
      </button>

      {open && (
        <ul className="absolute top-full right-0 z-10 mt-1 w-max overflow-hidden rounded-[6px] border border-purple-200 bg-white py-1 shadow-[0_8px_20px_rgba(0,0,0,0.08)]">
          {SORTS.map((key) => (
            <li key={key}>
              <button
                type="button"
                onClick={() => {
                  onChange(key);
                  setOpen(false);
                }}
                className="w-full px-3 py-2 text-left text-[11px] whitespace-nowrap"
                style={{ color: key === value ? DEEP : SELECT_TEXT }}
              >
                {SORT_LABEL[key]}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
