import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginResponse, RegisterRequest, Role } from './models';

interface Session {
  accessToken: string;
  userId: string;
  roles: Role[];
  expiresAt: number;
}

const STORAGE_KEY = 'ecom.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly state = signal<Session | null>(this.restore());

  readonly session = this.state.asReadonly();
  readonly isLoggedIn = computed(() => this.state() !== null);
  readonly isAdmin = computed(() => this.state()?.roles.includes('ADMIN') ?? false);
  readonly isCustomer = computed(() => this.state()?.roles.includes('CUSTOMER') ?? false);
  readonly userId = computed(() => this.state()?.userId ?? null);

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.identityUrl}/api/auth/login`, { email, password })
      .pipe(tap((r) => this.store(r)));
  }

  register(request: RegisterRequest): Observable<unknown> {
    return this.http.post(`${environment.identityUrl}/api/users/register`, request);
  }

  logout(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.state.set(null);
  }

  // Returns null and clears the session once the token has expired.
  token(): string | null {
    const s = this.state();
    if (!s) {
      return null;
    }
    if (s.expiresAt <= Date.now()) {
      this.logout();
      return null;
    }
    return s.accessToken;
  }

  hasRole(role: Role): boolean {
    return this.token() !== null && (this.state()?.roles.includes(role) ?? false);
  }

  private store(r: LoginResponse): void {
    const session: Session = {
      accessToken: r.accessToken,
      userId: r.userId,
      roles: r.roles,
      expiresAt: Date.now() + r.expiresInSeconds * 1000,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.state.set(session);
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const s = JSON.parse(raw) as Session;
      return s.expiresAt > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
