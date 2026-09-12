import {
  CaretRightIcon,
  CheckIcon,
  ClockCountdownIcon,
} from "@phosphor-icons/react";
import { useCallback, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { TopBar } from "../../../shared/ui/TopBar";
import { ConfirmActionDialog } from "../../group/components/ConfirmActionDialog";
import { groupErrorMessage } from "../../group/errors";
import { useResource } from "../../group/hooks/useResource";
import {
  applyBackground,
  cancelPurchaseRequest,
  createPurchaseRequest,
  fetchGroupShop,
  voteOnPurchaseRequest,
} from "../api";
import { MAP_TYPE_DEFAULT_IMAGE } from "../lib/defaultBackground";
import { MAP_TYPE_LABEL } from "../types";
import type { PurchaseRequestResponse, ShopBackgroundResponse } from "../types";

const OWNERSHIP_FILTERS = ["ALL", "OWNED", "NOT_OWNED"] as const;
type OwnershipFilter = (typeof OWNERSHIP_FILTERS)[number];
const OWNERSHIP_LABEL: Record<OwnershipFilter, string> = {
  ALL: "전체",
  OWNED: "보유 중",
  NOT_OWNED: "미보유",
};

export function GroupShopPage() {
  const { groupId } = useParams();
  return <GroupShopContent key={groupId} groupId={Number(groupId)} />;
}

function GroupShopContent({ groupId }: { groupId: number }) {
  const { user } = useAuth();
  const { showToast } = useToast();
  const loader = useCallback(() => fetchGroupShop(groupId), [groupId]);
  const { data, error, loading, retry } = useResource(loader);

  const [ownership, setOwnership] = useState<OwnershipFilter>("ALL");
  const [pickedId, setPickedId] = useState<number | null>(null);
  const [confirmingBuy, setConfirmingBuy] =
    useState<ShopBackgroundResponse | null>(null);
  const [confirmingApply, setConfirmingApply] =
    useState<ShopBackgroundResponse | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [voteBusy, setVoteBusy] = useState(false);
  const [voteError, setVoteError] = useState<string | null>(null);

  const items = useMemo(() => {
    if (!data) return [];
    if (ownership === "ALL") return data.catalog;
    return data.catalog.filter((item) =>
      ownership === "OWNED" ? item.owned : !item.owned,
    );
  }, [data, ownership]);

  const picked =
    data?.catalog.find((item) => item.backgroundId === pickedId) ?? null;
  const isOwner = data?.group.group.ownerId === user?.id;
  const hasVoting = data?.current !== null;

  const afterMutation = () => {
    setPickedId(null);
    retry();
  };

  const vote = async (agreed: boolean) => {
    if (!data?.current || voteBusy) return;
    setVoteBusy(true);
    setVoteError(null);
    try {
      await voteOnPurchaseRequest(groupId, data.current.requestId, agreed);
      retry();
    } catch (err) {
      setVoteError(groupErrorMessage(err));
    } finally {
      setVoteBusy(false);
    }
  };

  if (loading) {
    return (
      <>
        <TopBar title="그룹 상점" />
        <p className="py-20 text-center text-[13px] text-gray-500">
          불러오는 중…
        </p>
      </>
    );
  }

  if (error || !data) {
    return (
      <>
        <TopBar title="그룹 상점" />
        <div className="space-y-3 px-5 py-10">
          <FormAlert message={error ?? "상점을 불러오지 못했어요."} />
          <Button variant="secondary" onClick={retry}>
            다시 시도
          </Button>
        </div>
      </>
    );
  }

  const canCancelCurrent =
    data.current !== null && (data.current.requestedBy === user?.id || isOwner);
  const mapTypeLabel = MAP_TYPE_LABEL[data.group.group.mapType];

  return (
    <>
      <div className="flex min-h-full flex-col">
        <TopBar
          title={
            <span className="mx-10 block truncate">
              {data.group.group.name} 상점
            </span>
          }
        />

        <div className="grow px-[22px] pb-10">
          <section className="relative mt-[18px] h-[160px] overflow-hidden rounded-[10px] border border-purple-200">
            <img
              src={
                data.active.imageUrl ??
                MAP_TYPE_DEFAULT_IMAGE[data.group.group.mapType]
              }
              alt={data.active.name ?? `기본 ${mapTypeLabel} 배경`}
              className="h-full w-full object-cover"
            />

            {!data.active.imageUrl && (
              <span className="absolute bottom-[8px] left-[8px] rounded-full bg-black/45 px-3 py-1 text-[11px] font-semibold text-white">
                {mapTypeLabel} 기본 배경
              </span>
            )}

            <Link
              to={`/challenges/groups/${groupId}/points`}
              className="absolute top-[8px] right-[6px] flex h-[27px] items-center gap-[5px] rounded-full bg-white/90 px-3 transition-colors hover:bg-white"
            >
              <PixelIcon src={pixelIcons.pointHistory} size={15} />
              <span className="text-[14px] leading-none font-bold text-purple-500">
                {data.balance.toLocaleString()}P
              </span>
              <CaretRightIcon
                size={12}
                className="text-purple-400"
                aria-hidden
              />
            </Link>
          </section>

          {data.current && (
            <section className="mt-[14px] rounded-[12px] border-2 border-purple-300 bg-white p-4">
              <div className="flex items-center gap-1.5 text-purple-600">
                <ClockCountdownIcon size={14} weight="bold" />
                <span className="text-[11px] font-bold">
                  진행 중인 제안 · {deadlineLabel(data.current.expiresAt)}
                </span>
              </div>

              <div className="mt-2 flex items-center gap-3">
                <img
                  src={data.current.imageUrl}
                  alt=""
                  aria-hidden
                  className="h-12 w-20 shrink-0 rounded-lg object-cover"
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[14px] font-semibold text-gray-900">
                    {data.current.backgroundName}
                  </p>
                  <p className="text-[12px] text-gray-500">
                    {data.current.requestedByNickname ?? "탈퇴한 멤버"} 님의
                    제안 · {data.current.price.toLocaleString()}P
                  </p>
                </div>
              </div>

              <VoteProgress request={data.current} />

              <FormAlert message={voteError} />

              <div className="mt-3 flex items-center gap-2">
                {data.current.myAgreed === null ? (
                  <>
                    <Button
                      className="flex-1"
                      loading={voteBusy}
                      onClick={() => vote(true)}
                    >
                      찬성
                    </Button>
                    <Button
                      className="flex-1"
                      variant="secondary"
                      loading={voteBusy}
                      onClick={() => vote(false)}
                    >
                      반대
                    </Button>
                  </>
                ) : (
                  <p className="text-[13px] text-purple-600">
                    이미 {data.current.myAgreed ? "찬성" : "반대"}에 투표했어요.
                  </p>
                )}

                {canCancelCurrent && (
                  <button
                    type="button"
                    onClick={() => setConfirmingCancel(true)}
                    className="ml-auto shrink-0 text-[12px] text-gray-500 underline"
                  >
                    제안 취소
                  </button>
                )}
              </div>
            </section>
          )}

          <div
            role="tablist"
            aria-label="보유 여부"
            className="mt-[16px] flex h-[26px] w-[164px] items-center rounded-[6px] bg-gray-100 p-px"
          >
            {OWNERSHIP_FILTERS.map((key) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={ownership === key}
                onClick={() => setOwnership(key)}
                className={`h-[24px] flex-1 rounded-[5.5px] text-[11px] font-semibold ${
                  ownership === key
                    ? "border border-purple-200 bg-purple-100 text-purple-700"
                    : "text-gray-500"
                }`}
              >
                {OWNERSHIP_LABEL[key]}
              </button>
            ))}
          </div>

          {items.length === 0 ? (
            <p className="py-16 text-center text-[12px] text-gray-500">
              조건에 맞는 배경이 없어요.
            </p>
          ) : (
            <ul className="mt-[12px] space-y-[12px]">
              {items.map((item) => (
                <li key={item.backgroundId}>
                  <BackgroundCard
                    item={item}
                    picked={pickedId === item.backgroundId}
                    onSelect={() =>
                      setPickedId(
                        pickedId === item.backgroundId
                          ? null
                          : item.backgroundId,
                      )
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
            balance={data.balance}
            isOwner={isOwner}
            hasVoting={hasVoting}
            onBuy={() => setConfirmingBuy(picked)}
            onApply={() => setConfirmingApply(picked)}
          />
        )}
      </div>

      {confirmingBuy && (
        <ConfirmActionDialog
          title="배경 구매를 제안할까요?"
          description={`${confirmingBuy.name}(${confirmingBuy.price.toLocaleString()}P)을 사자고 그룹에 제안해요. 과반이 찬성하면 그룹 포인트로 구매돼요.`}
          confirmLabel="제안하기"
          onConfirm={async () => {
            await createPurchaseRequest(groupId, confirmingBuy.backgroundId);
            afterMutation();
            showToast("구매를 제안했어요.");
          }}
          onClose={() => setConfirmingBuy(null)}
        />
      )}

      {confirmingApply && (
        <ConfirmActionDialog
          title="이 배경을 적용할까요?"
          description={`${confirmingApply.name}(으)로 그룹 화면의 배경이 바뀌어요.`}
          confirmLabel="적용하기"
          onConfirm={async () => {
            await applyBackground(groupId, confirmingApply.backgroundId);
            afterMutation();
            showToast("배경을 적용했어요.");
          }}
          onClose={() => setConfirmingApply(null)}
        />
      )}

      {confirmingCancel && data.current && (
        <ConfirmActionDialog
          title="제안을 취소할까요?"
          description="진행 중인 투표가 종료돼요."
          confirmLabel="취소하기"
          onConfirm={async () => {
            await cancelPurchaseRequest(groupId, data.current!.requestId);
            afterMutation();
          }}
          onClose={() => setConfirmingCancel(false)}
        />
      )}
    </>
  );
}

function ActionBar({
  item,
  balance,
  isOwner,
  hasVoting,
  onBuy,
  onApply,
}: {
  item: ShopBackgroundResponse;
  balance: number;
  isOwner: boolean;
  hasVoting: boolean;
  onBuy(): void;
  onApply(): void;
}) {
  const short = item.price - balance;
  const canBuy = short <= 0 && !hasVoting && !item.voting;

  return (
    <div className="sticky bottom-0 z-20 border-t border-purple-200 bg-white px-[22px] py-[12px]">
      <div className="flex items-center gap-[10px]">
        <img
          src={item.imageUrl}
          alt=""
          aria-hidden
          className="h-11 w-16 shrink-0 rounded-[10px] bg-purple-50 object-cover"
        />

        <span className="min-w-0 flex-1">
          <span className="block truncate text-[13px] font-semibold text-gray-900">
            {item.name}
          </span>
          <span className="mt-[3px] flex items-center gap-[4px] text-[11px]">
            {item.owned ? (
              <span className="text-purple-600">
                {item.active ? "적용 중" : "보유 중"}
              </span>
            ) : (
              <>
                <PixelIcon src={pixelIcons.pointHistory} size={12} />
                <span className="font-semibold text-gray-900 tabular-nums">
                  {item.price.toLocaleString()}P
                </span>
                {short > 0 && (
                  <span className="text-[#D94B2B]">
                    · {short.toLocaleString()}P 모자라요
                  </span>
                )}
                {item.voting && (
                  <span className="text-gray-500">· 투표 중</span>
                )}
              </>
            )}
          </span>
        </span>

        {item.owned ? (
          <button
            type="button"
            onClick={onApply}
            disabled={item.active || !isOwner}
            className={`h-[40px] w-[92px] shrink-0 rounded-[10px] text-[13px] font-semibold focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-40 ${
              item.active
                ? "border border-purple-200 bg-white text-purple-700"
                : "bg-purple-500 text-white hover:bg-purple-600"
            }`}
          >
            {item.active ? "적용 중" : "적용하기"}
          </button>
        ) : (
          <button
            type="button"
            onClick={onBuy}
            disabled={!canBuy}
            className="h-[40px] w-[92px] shrink-0 rounded-[10px] bg-purple-500 text-[13px] font-semibold text-white hover:bg-purple-600 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-40"
          >
            구매 제안
          </button>
        )}
      </div>
      {item.owned && !isOwner && !item.active && (
        <p className="mt-2 text-[11px] text-gray-500">
          그룹장만 배경을 적용할 수 있어요.
        </p>
      )}
      {!item.owned && hasVoting && !item.voting && (
        <p className="mt-2 text-[11px] text-gray-500">
          이미 진행 중인 제안이 있어요. 먼저 끝나야 새로 제안할 수 있어요.
        </p>
      )}
    </div>
  );
}

function BackgroundCard({
  item,
  picked,
  onSelect,
}: {
  item: ShopBackgroundResponse;
  picked: boolean;
  onSelect(): void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={picked}
      className={`block w-full overflow-hidden rounded-[12px] bg-white text-left focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none ${
        picked
          ? "border-2 border-purple-500"
          : `border ${item.active ? "border-purple-500" : "border-purple-200"}`
      }`}
    >
      <div className="relative aspect-video w-full bg-purple-50">
        <img
          src={item.imageUrl}
          alt=""
          aria-hidden
          className="h-full w-full object-cover"
        />
        {item.active && (
          <span className="absolute top-[8px] right-[8px] flex h-[20px] w-[20px] items-center justify-center rounded-full bg-purple-500">
            <CheckIcon size={11} weight="bold" color="#fff" aria-hidden />
          </span>
        )}
      </div>

      <div className="flex items-center justify-between gap-2 px-3 py-[10px]">
        <span className="min-w-0 truncate text-[13px] font-semibold text-gray-800">
          {item.name}
        </span>

        <span
          className={`flex h-[22px] shrink-0 items-center justify-center gap-[3px] rounded-[5.5px] border border-purple-200 px-2 text-[11px] font-semibold ${
            item.owned
              ? "bg-purple-100 text-purple-700"
              : "bg-gray-50 text-gray-700"
          }`}
        >
          {item.owned ? (
            item.active ? (
              "적용 중"
            ) : (
              "보유 중"
            )
          ) : item.voting ? (
            "투표 중"
          ) : (
            <>
              <PixelIcon src={pixelIcons.pointHistory} size={11} />
              {item.price}P
            </>
          )}
        </span>
      </div>
    </button>
  );
}

/** "오늘 마감" / "1일 후 마감" / "3일 후 마감". 만료된 건 조회 시점에 자동 부결돼 화면에 안 남는다. */
function deadlineLabel(expiresAt: string): string {
  const days = Math.ceil(
    (new Date(expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
  );
  return days <= 0 ? "오늘 마감" : `${days}일 후 마감`;
}

/** 찬반 막대와 "몇 명 더 필요한지"를 보여준다. 과반 인원(requiredCount)은 찬성만으로
 * 채워야 하는 목표치라 disagree/undecided 는 막대에서 회색으로만 구분한다. */
function VoteProgress({ request }: { request: PurchaseRequestResponse }) {
  const { agreeCount, disagreeCount, totalMembers, requiredCount } = request;
  const undecided = Math.max(0, totalMembers - agreeCount - disagreeCount);
  const pct = (n: number) => (totalMembers > 0 ? (n / totalMembers) * 100 : 0);
  const needMore = Math.max(0, requiredCount - agreeCount);

  return (
    <div className="mt-3">
      <div className="flex h-[8px] w-full overflow-hidden rounded-full bg-gray-100">
        <span
          className="h-full bg-purple-500"
          style={{ width: `${pct(agreeCount)}%` }}
        />
        <span
          className="h-full bg-gray-300"
          style={{ width: `${pct(disagreeCount)}%` }}
        />
      </div>
      <p className="mt-1.5 text-[11px] text-gray-600">
        찬성 {agreeCount} · 반대 {disagreeCount} · 미투표 {undecided} ·{" "}
        {needMore > 0 ? (
          <span className="font-semibold text-purple-600">
            {needMore}명 더 찬성하면 가결
          </span>
        ) : (
          <span className="font-semibold text-purple-600">가결 조건 충족</span>
        )}
      </p>
    </div>
  );
}
