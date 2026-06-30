import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';
import { PageHeaderComponent } from '../../src/app/shared/page-header/page-header.component';

describe('PageHeaderComponent', () => {
  let fixture: ComponentFixture<PageHeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PageHeaderComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(PageHeaderComponent);
  });

  it('renders title', () => {
    fixture.componentRef.setInput('title', 'Test title');
    fixture.detectChanges();
    const h1 = fixture.nativeElement.querySelector('h1');
    expect(h1?.textContent?.trim()).toBe('Test title');
  });

  it('renders action link when configured', () => {
    fixture.componentRef.setInput('title', 'Sources');
    fixture.componentRef.setInput('actionLabel', 'Add');
    fixture.componentRef.setInput('actionLink', '/sources/new');
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a.btn-primary');
    expect(link?.textContent?.trim()).toBe('Add');
    expect(link?.getAttribute('href')).toBe('/sources/new');
  });
});