/** Standard pagination metadata returned by collection endpoints. */
export interface Pagination {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Standard success envelope. `pagination` is present only for collections. */
export interface ApiEnvelope<T> {
  success: true;
  message: string;
  data: T;
  pagination?: Pagination;
}

/** A single field-level error inside the standard error envelope. */
export interface ApiErrorItem {
  field?: string | null;
  code: string;
  message: string;
}

/** Standard error envelope. */
export interface ApiErrorEnvelope {
  success: false;
  message: string;
  errors?: ApiErrorItem[];
  timestamp?: string;
  path?: string;
  requestId?: string;
}

/**
 * The normalized result of a successful API call.
 *
 * The envelope is unwrapped once by the API client so callers work with domain data, not with
 * transport structure.
 */
export interface ApiResult<T> {
  data: T;
  message: string;
  pagination?: Pagination;
}
