import type { ResReviewVo } from '../typings';

export function canFillReview(row: ResReviewVo, currentUserId: string | undefined, permitted: boolean): boolean {
  return permitted && row.reviewStatus === 'PENDING' && !!currentUserId && !!row.reviewerUserId && row.reviewerUserId === currentUserId;
}
