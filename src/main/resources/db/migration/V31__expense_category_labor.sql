-- Day labour / casual workers paid from the till, separate from monthly SALARIES.
ALTER TABLE expenses DROP CONSTRAINT IF EXISTS expenses_category_check;
ALTER TABLE expenses ADD CONSTRAINT expenses_category_check
    CHECK (category IN ('RENT', 'ELECTRICITY', 'SALARIES', 'LABOR', 'TRANSPORT', 'INTERNET', 'MAINTENANCE', 'OTHER'));
