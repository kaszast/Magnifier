# Plan: Add untracked files to Git

The user wants to add all files currently not in Git to the repository.
Upon investigation, the following untracked files were found:
- `english_cover.png`
- `english_cover_pici.png`
- `magyar_cover.png`
- `magyar_cover_pici.png`
- `.idea/shell_pids*.tmp` (Temporary IDE files)
- `magnifier/release/magnifier-release/` (Build artifacts)

## Proposed Changes

I will add the image assets to the repository as they appear to be part of the project source/documentation.
However, I will **not** add the temporary `.tmp` files or the `release` folder, as these are build artifacts and should typically be ignored. Instead, I will update the `.gitignore` to ensure they are properly excluded in the future.

### Git Configuration

#### [MODIFY] [.gitignore](file:///home/maci/androidstudio%20projects/magnifier/.gitignore)
- Add `/magnifier/release/` to ignore build/release artifacts.
- Add `.idea/*.tmp` to ignore temporary IDE files.

### Git Actions

- `git add english_cover.png english_cover_pici.png magyar_cover.png magyar_cover_pici.png`
- `git add .gitignore`
- `git commit -m "Add cover images and update .gitignore"`

## Verification Plan

### Manual Verification
- Run `git status` to verify that the intended files are staged and the unintended ones are ignored.
