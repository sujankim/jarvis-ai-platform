import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Agents } from './agents';

describe('Agents', () => {
  let component: Agents;
  let fixture: ComponentFixture<Agents>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Agents],
    }).compileComponents();

    fixture = TestBed.createComponent(Agents);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
