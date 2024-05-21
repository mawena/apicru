<?php
namespace Mawena\Apicru\Traits;

trait PermissionCheckerTrait
{
    public function check($actions, $subject, $connectedUser)
    {
        foreach ($connectedUser->ability_rules as $rules) {
            if (in_array($subject, $rules["subject"]) || in_array("all", $rules["subject"])) {
                if (in_array("manage", $rules["action"])) {
                    return true;
                }
                foreach ($actions as $action) {
                    if (in_array($action, $rules["action"]))
                        return true;
                }
            }
        }
        return false;
    }
}
