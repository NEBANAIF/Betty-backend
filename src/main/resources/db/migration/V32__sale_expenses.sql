-- An expense can belong to one sale (labour / delivery / loading entered at checkout). NULL = a general expense.
ALTER TABLE expenses ADD COLUMN sale_id UUID REFERENCES sales (id);
CREATE INDEX idx_expenses_sale ON expenses (sale_id);
