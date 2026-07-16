import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Voice } from './voice';

describe('Voice', () => {
  let component: Voice;
  let fixture: ComponentFixture<Voice>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Voice],
    }).compileComponents();

    fixture = TestBed.createComponent(Voice);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
