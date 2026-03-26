export type WaitingRouteState =
  | {
      mode: "prep_locked" | "next_business_day";
      brandName: string;
      districtName: string;
      nextPath: string;
      endTimestampMs?: number;
      targetDay?: number;
    }
  | {
      mode: "season_starting";
      seasonNumber?: number | null;
      nextPath?: string;
    };
