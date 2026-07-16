import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Memory } from './memory';

describe('Memory', () => {
  let component: Memory;
  let fixture: ComponentFixture<Memory>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Memory],
    }).compileComponents();

    fixture = TestBed.createComponent(Memory);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
