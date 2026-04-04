# Publish MSDA to GitHub (master branch)

## 1) Initialize git (if needed)

```powershell
git init
git add .
git commit -m "Initial MSDA commit"
```

## 2) Ensure branch is `master`

```powershell
git branch -M master
```

## 3) Create repository on GitHub

### Option A: GitHub CLI (recommended)

```powershell
gh auth login
gh repo create MSDA --private --source . --remote origin --push
```

Use `--public` instead of `--private` if you want public visibility.

### Option B: Manual (GitHub website)

1. Create repository named `MSDA` on GitHub (no README/license/gitignore).
2. Then run:

```powershell
git remote add origin https://github.com/<YOUR_USERNAME>/MSDA.git
git push -u origin master
```

## 4) Verify

```powershell
git remote -v
git branch --show-current
git status
```

Expected:
- remote `origin` points to `MSDA`
- current branch is `master`
- working tree clean
