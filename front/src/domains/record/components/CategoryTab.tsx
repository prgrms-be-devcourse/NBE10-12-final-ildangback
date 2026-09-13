import { CATEGORY_LABEL } from "../lib/category";
import type { PersonalStatsData } from "../lib/personalStats";
import { designArt } from "../../../shared/ui/designArt";
import { DonutChart } from "./DonutChart";
import { BANNER, CARD, CARD_TITLE, DEEP, LIGHT, TRACK } from "./statsTokens";

// 순위가 높은 것부터 진한 색을 준다. 성공률 막대와 미인증 도넛이 같이 쓴다.
const PURPLE_RAMP = [
  "#5A3E89",
  "#6D4BA7",
  "#774AD3",
  "#987BD2",
  "#AC89E6",
  "#C9B6EC",
  "#E1D6F4",
];

const GOOD = {
  bg: "#F9FEF9",
  border: "#E9F8EB",
  circle: "#EAF8EA",
  ink: "#2E9E4B",
};
const WARN = {
  bg: "#FDFBF9",
  border: "#FFECE4",
  circle: "#FFEEE6",
  ink: "#E8752F",
};

export function CategoryTab({ stats }: { stats: PersonalStatsData }) {
  if (stats.categories.length === 0) {
    return (
      <p className="py-20 text-center text-[13px] text-gray-500">
        아직 카테고리별로 나눌 기록이 없어요.
      </p>
    );
  }

  const byRate = [...stats.categories].sort(
    (a, b) => b.successRate - a.successRate,
  );
  const best = byRate[0];
  const worst = byRate[byRate.length - 1];

  const byShare = [...stats.categories].sort(
    (a, b) => b.missedShare - a.missedShare,
  );
  // 배너는 두 카테고리를 묶어 말한다. 하나뿐이면 할 말이 안 된다.
  const topTwo = byShare.length >= 2 ? byShare.slice(0, 2) : null;
  const topTwoShare = topTwo
    ? topTwo.reduce((sum, s) => sum + s.missedShare, 0)
    : 0;

  return (
    <>
      <section className={`mt-[4px] ${CARD} pt-[15px] pb-[27px]`}>
        <h2 className={`${CARD_TITLE} pl-[12px]`}>카테고리별 성공률</h2>

        <ul className="mt-[20px] space-y-[10px] pr-[14px] pl-[12px]">
          {byRate.map((stat, index) => (
            <li key={stat.category} className="flex items-center gap-[9px]">
              <span className="w-[24px] shrink-0 text-[11px] text-gray-900">
                {CATEGORY_LABEL[stat.category]}
              </span>
              <span
                className="h-[12px] flex-1 overflow-hidden rounded-full"
                style={{ backgroundColor: TRACK }}
              >
                <span
                  className="block h-full rounded-full"
                  style={{
                    width: `${stat.successRate}%`,
                    backgroundColor: PURPLE_RAMP[index % PURPLE_RAMP.length],
                  }}
                />
              </span>
              <span className="w-[26px] shrink-0 text-right text-[11px] font-bold text-gray-900">
                {stat.successRate}%
              </span>
            </li>
          ))}
        </ul>
      </section>

      <div className="mt-[11px] flex gap-[11px]">
        <SideCard
          tone={GOOD}
          art={designArt.bestCategory}
          caption="가장 꾸준해요"
          name={CATEGORY_LABEL[best.category]}
          detail={`${best.checkIns}회`}
        />
        <SideCard
          tone={WARN}
          art={designArt.worstCategory}
          caption="조금 더 챙겨봐요"
          name={CATEGORY_LABEL[worst.category]}
          detail={`${worst.checkIns}회`}
        />
      </div>

      <section className={`mt-[13px] ${CARD} pt-[15px] pb-[7px]`}>
        <h2 className={`${CARD_TITLE} pl-[12px]`}>놓친 인증 비율</h2>

        <div className="mt-[20px] flex items-center pl-[12px]">
          <DonutChart
            segments={byShare.map((stat, index) => ({
              value: stat.missedShare,
              color: PURPLE_RAMP[index % PURPLE_RAMP.length],
            }))}
            size={135}
            thickness={26}
            trackColor="transparent"
            label="카테고리별 놓친 인증 비율"
          />

          <ul className="ml-[23px] flex-1 space-y-[5px] pr-[16px]">
            {byShare.map((stat, index) => (
              <li
                key={stat.category}
                className="flex items-center gap-[8px] text-[13px]"
              >
                <span
                  className="h-[9px] w-[9px] shrink-0 rounded-full"
                  style={{
                    backgroundColor: PURPLE_RAMP[index % PURPLE_RAMP.length],
                  }}
                />
                <span className="flex-1 text-gray-900">
                  {CATEGORY_LABEL[stat.category]}
                </span>
                <span className="text-gray-500">{stat.missedShare}%</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {topTwo && (
        <section
          className="mt-[14px] flex h-[72px] items-center gap-[12px] rounded-[7px] pl-[12px]"
          style={{ backgroundColor: BANNER }}
        >
          <DonutChart
            segments={[
              { value: topTwoShare, color: DEEP },
              { value: 100 - topTwoShare, color: LIGHT },
            ]}
            size={58}
            thickness={19}
            trackColor="transparent"
            label={`상위 두 카테고리가 전체 미인증의 ${topTwoShare}퍼센트`}
          />
          <p className="text-[14px] leading-[21px] text-gray-900">
            {CATEGORY_LABEL[topTwo[0].category]}과{" "}
            {CATEGORY_LABEL[topTwo[1].category]} 인증에서
            <br />
            <span className="font-bold" style={{ color: DEEP }}>
              전체 미인증의 {topTwoShare}%
            </span>
            가 발생했어요
          </p>
        </section>
      )}
    </>
  );
}

function SideCard({
  tone,
  art,
  caption,
  name,
  detail,
}: {
  tone: { bg: string; border: string; circle: string; ink: string };
  art: string;
  caption: string;
  name: string;
  detail: string;
}) {
  return (
    <div
      className="flex h-[69px] flex-1 items-center gap-[11px] rounded-[6.65px] border-[0.7px] pl-[12px]"
      style={{ backgroundColor: tone.bg, borderColor: tone.border }}
    >
      <span
        className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-full"
        style={{ backgroundColor: tone.circle }}
      >
        <img src={art} alt="" className="h-[26px] object-contain pixelated" />
      </span>
      <div className="min-w-0">
        <p className="text-[10px] leading-none" style={{ color: tone.ink }}>
          {caption}
        </p>
        <p className="mt-[7px] text-[16px] leading-none font-bold text-gray-900">
          {name}
        </p>
        <p
          className="mt-[6px] text-[12px] leading-none"
          style={{ color: tone.ink }}
        >
          <span className="font-bold">{detail}</span> 인증
        </p>
      </div>
    </div>
  );
}
