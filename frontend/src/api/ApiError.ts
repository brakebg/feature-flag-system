/** A field error of a `validation` problem (spec 9.1). */
export interface FieldError {
  field: string;
  message: string;
}

/** An RFC 9457 problem detail from the backend, or a network failure (status 0). */
export class ApiError extends Error {
  readonly status: number;
  readonly type: string;
  readonly detail: string;
  readonly errors: FieldError[];
  readonly headers: Headers | null;

  constructor(
    status: number,
    type: string,
    detail: string,
    errors: FieldError[] = [],
    headers: Headers | null = null,
  ) {
    super(detail || `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.type = type;
    this.detail = detail;
    this.errors = errors;
    this.headers = headers;
  }

  /** The problem type suffix, e.g. `duplicate-key`. */
  get kind(): string {
    const i = this.type.lastIndexOf('/');
    return i >= 0 ? this.type.slice(i + 1) : this.type;
  }

  get isNetwork(): boolean {
    return this.status === 0;
  }
}
