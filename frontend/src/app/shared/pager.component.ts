import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-pager',
  standalone: true,
  template: `
    @if (totalPages() > 1) {
      <div class="row" style="margin-top:1rem">
        <button type="button" class="secondary" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">
          Previous
        </button>
        <span class="muted">Page {{ page() + 1 }} of {{ totalPages() }}</span>
        <button
          type="button"
          class="secondary"
          [disabled]="page() + 1 >= totalPages()"
          (click)="pageChange.emit(page() + 1)"
        >
          Next
        </button>
      </div>
    }
  `,
})
export class PagerComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();
}
