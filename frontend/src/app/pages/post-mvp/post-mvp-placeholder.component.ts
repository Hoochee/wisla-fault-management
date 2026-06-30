import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'app-post-mvp-placeholder',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="placeholder">
      <h1>{{ title }}</h1>
      <p>Раздел запланирован на post-MVP.</p>
      <a routerLink="/">← На Dashboard</a>
    </div>
  `,
  styles: [
    `
      .placeholder { text-align: center; padding: 4rem 2rem; }
      h1 { color: #fff; margin-bottom: 0.5rem; }
      p { color: var(--text-muted); margin-bottom: 1.5rem; }
      a { color: var(--accent); text-decoration: none; }
    `,
  ],
})
export class PostMvpPlaceholderComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  title = 'Post-MVP';

  ngOnInit(): void {
    this.title = this.route.snapshot.data['title'] ?? 'Post-MVP';
  }
}
