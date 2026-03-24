import client from "./client";

// --- 타입 정의 ---

export interface UserInfo {
  subject: string;
  provider: string;
  email: string;
  name: string;
  picture: string;
  nickname: string;
  role: string;
}

export interface UserPointsResponse {
  currentPoints: number;
}

export interface UserRecord {
  seasonNumber: number;
  rank: number | null;
  location: string;
  popupName: string;
  profit: number;
  rewardPoint: number;
}

export interface UserRecordsResponse {
  records: UserRecord[];
}

// --- API 함수 ---

/** 현재 로그인 유저 정보 조회 */
export function getUser() {
  return client.get<UserInfo>("/api/users");
}

/** 보유 포인트 조회 (data unwrap) */
export async function getUserPoints() {
  const { data } = await client.get<UserPointsResponse>("/api/users/points");
  return data;
}

/** 닉네임 수정 */
export function patchNickname(nickname: string) {
  return client.patch<UserInfo>("/api/users/nickname", { nickname });
}

/** 시즌별 기록 조회 */
export function getUserRecords() {
  return client.get<UserRecordsResponse>("/api/users/records");
}
